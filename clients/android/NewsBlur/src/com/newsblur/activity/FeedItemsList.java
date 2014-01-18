package com.newsblur.activity;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.newsblur.R;
import com.newsblur.database.DatabaseConstants;
import com.newsblur.database.FeedProvider;
import com.newsblur.domain.Feed;
import com.newsblur.fragment.DeleteFeedFragment;
import com.newsblur.fragment.FeedItemListFragment;
import com.newsblur.network.APIManager;
import com.newsblur.network.MarkFeedAsReadTask;
import com.newsblur.util.DefaultFeedView;
import com.newsblur.util.FeedUtils;
import com.newsblur.util.PrefsUtils;
import com.newsblur.util.ReadFilter;
import com.newsblur.util.StoryOrder;

public class FeedItemsList extends ItemsList {

	public static final String EXTRA_FEED = "feedId";
	public static final String EXTRA_FEED_TITLE = "feedTitle";
	public static final String EXTRA_FOLDER_NAME = "folderName";
	private String feedId;
	private String feedTitle;
	private String folderName;
	private APIManager apiManager;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		apiManager = new APIManager(this);
		feedId = getIntent().getStringExtra(EXTRA_FEED);
        feedTitle = getIntent().getStringExtra(EXTRA_FEED_TITLE);
        folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        
		final Uri feedUri = FeedProvider.FEEDS_URI.buildUpon().appendPath(feedId).build();
		Cursor cursor = getContentResolver().query(feedUri, null, DatabaseConstants.getStorySelectionFromState(currentState), null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            Feed feed = Feed.fromCursor(cursor);
            setTitle(feed.title);
        }
        cursor.close();

		itemListFragment = (FeedItemListFragment) fragmentManager.findFragmentByTag(FeedItemListFragment.class.getName());
		if (itemListFragment == null) {
			itemListFragment = FeedItemListFragment.newInstance(feedId, currentState, getStoryOrder(), getDefaultFeedView());
			itemListFragment.setRetainInstance(true);
			FragmentTransaction listTransaction = fragmentManager.beginTransaction();
			listTransaction.add(R.id.activity_itemlist_container, itemListFragment, FeedItemListFragment.class.getName());
			listTransaction.commit();
		}
	}
	
	public void deleteFeed() {
		DialogFragment deleteFeedFragment = DeleteFeedFragment.newInstance(Long.parseLong(feedId), feedTitle, folderName);
		deleteFeedFragment.show(fragmentManager, "dialog");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (!super.onOptionsItemSelected(item)) {
			if (item.getItemId() == R.id.menu_delete_feed) {
				deleteFeed();
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}
	
	@Override
	public void markItemListAsRead() {
		new MarkFeedAsReadTask(this, apiManager) {
			@Override
			protected void onPostExecute(Boolean result) {
				if (result.booleanValue()) {
					ContentValues values = new ContentValues();
					values.put(DatabaseConstants.FEED_NEGATIVE_COUNT, 0);
					values.put(DatabaseConstants.FEED_NEUTRAL_COUNT, 0);
					values.put(DatabaseConstants.FEED_POSITIVE_COUNT, 0);
					getContentResolver().update(FeedProvider.FEEDS_URI.buildUpon().appendPath(feedId).build(), values, null, null);
					setResult(RESULT_OK);
					Toast.makeText(FeedItemsList.this, R.string.toast_marked_feed_as_read, Toast.LENGTH_LONG).show();
					finish();
				} else {
					Toast.makeText(FeedItemsList.this, R.string.toast_error_marking_feed_as_read, Toast.LENGTH_LONG).show();
				}
			}
		}.execute(feedId);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.feed_itemslist, menu);
		return true;
	}

	@Override
	public void triggerRefresh(int page) {
		if (!stopLoading) {
			setSupportProgressBarIndeterminateVisibility(true);
            FeedUtils.updateFeed(this, this, feedId, page, getStoryOrder(), PrefsUtils.getReadFilterForFeed(this, feedId));
		}
	}

    @Override
    protected StoryOrder getStoryOrder() {
        return PrefsUtils.getStoryOrderForFeed(this, feedId);
    }

    @Override
    public void updateStoryOrderPreference(StoryOrder newValue) {
        PrefsUtils.setStoryOrderForFeed(this, feedId, newValue);
    }
    
    @Override
    protected void updateReadFilterPreference(ReadFilter newValue) {
        PrefsUtils.setReadFilterForFeed(this, feedId, newValue);
    }
    
    @Override
    protected ReadFilter getReadFilter() {
        return PrefsUtils.getReadFilterForFeed(this, feedId);
    }

    @Override
    protected DefaultFeedView getDefaultFeedView() {
        return PrefsUtils.getDefaultFeedViewForFeed(this, feedId);
    }

    @Override
    public void defaultFeedViewChanged(DefaultFeedView value) {
        PrefsUtils.setDefaultFeedViewForFeed(this, feedId, value);
        if (itemListFragment != null) {
            itemListFragment.setDefaultFeedView(value);
        }
    }
}
