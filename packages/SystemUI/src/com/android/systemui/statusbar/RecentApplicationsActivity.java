/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.systemui.statusbar;

import com.android.ex.carousel.CarouselView;
import com.android.ex.carousel.CarouselRS.CarouselCallback;
import com.android.internal.R;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.IThumbnailReceiver;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

public class RecentApplicationsActivity extends Activity {
    private static final String TAG = "RecentApplicationsActivity";
    private static boolean DBG = true;
    private static final int CARD_SLOTS = 56;
    private static final int VISIBLE_SLOTS = 7;
    private static final int MAX_TASKS = VISIBLE_SLOTS * 2;
    private ActivityManager mActivityManager;
    private List<RunningTaskInfo> mRunningTaskList;
    private boolean mPortraitMode = true;
    private ArrayList<ActivityDescription> mActivityDescriptions
            = new ArrayList<ActivityDescription>();
    private CarouselView mCarouselView;
    private View mNoRecentsView;
    private Bitmap mBlankBitmap = Bitmap.createBitmap(
            new int[] {0xff808080, 0xffffffff, 0xff808080, 0xffffffff}, 2, 2, Config.RGB_565);

    static class ActivityDescription {
        int id;
        Bitmap thumbnail; // generated by Activity.onCreateThumbnail()
        Drawable icon; // application package icon
        String label; // application package label
        String description; // generated by Activity.onCreateDescription()
        Intent intent; // launch intent for application
        Matrix matrix; // arbitrary rotation matrix to correct orientation
        int position; // position in list

        public ActivityDescription(Bitmap _thumbnail,
                Drawable _icon, String _label, String _desc, int _id, int _pos)
        {
            thumbnail = _thumbnail;
            icon = _icon;
            label = _label;
            description = _desc;
            id = _id;
            position = _pos;
        }

        public void clear() {
            icon = null;
            thumbnail = null;
            label = null;
            description = null;
            intent = null;
            matrix = null;
            id = -1;
            position = -1;
        }
    };

    private ActivityDescription findActivityDescription(int id) {
        for (int i = 0; i < mActivityDescriptions.size(); i++) {
            ActivityDescription item = mActivityDescriptions.get(i);
            if (item != null && item.id == id) {
                return item;
            }
        }
        return null;
    }

    final CarouselCallback mCarouselCallback = new CarouselCallback() {

        public void onAnimationFinished() {

        }

        public void onAnimationStarted() {

        }

        public void onCardSelected(int n) {
            if (n < mActivityDescriptions.size()) {
                ActivityDescription item = mActivityDescriptions.get(n);
                // prepare a launch intent and send it
                if (item.intent != null) {
                    item.intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                    try {
                        if (DBG) Log.v(TAG, "Starting intent " + item.intent);
                        startActivity(item.intent);
                        //overridePendingTransition(R.anim.zoom_enter, R.anim.zoom_exit);
                    } catch (ActivityNotFoundException e) {
                        if (DBG) Log.w("Recent", "Unable to launch recent task", e);
                    }
                    finish();
                }
            }
        }

        public void onInvalidateTexture(int n) {

        }

        public void onRequestGeometry(int n) {

        }

        public void onInvalidateGeometry(int n) {

        }

        public void onRequestTexture(final int n) {
            if (DBG) Log.v(TAG, "onRequestTexture(" + n + ")");
            if (n < mActivityDescriptions.size()) {
                mCarouselView.post(new Runnable() {
                    public void run() {
                        ActivityDescription info = mActivityDescriptions.get(n);
                        if (info != null) {
                            if (DBG) Log.v(TAG, "FOUND ACTIVITY THUMBNAIL " + info.thumbnail);
                            Bitmap bitmap = info.thumbnail == null ? mBlankBitmap : info.thumbnail;
                            mCarouselView.setTextureForItem(n, bitmap);
                        } else {
                            if (DBG) Log.v(TAG, "FAILED TO GET ACTIVITY THUMBNAIL FOR ITEM " + n);
                        }
                    }
                });
            }
        }
    };

    private final IThumbnailReceiver mThumbnailReceiver = new IThumbnailReceiver.Stub() {

        public void finished() throws RemoteException {

        }

        public void newThumbnail(final int id, final Bitmap bitmap, CharSequence description)
                throws RemoteException {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (DBG) Log.v(TAG, "New thumbnail for id=" + id + ", dimensions=" + w + "x" + h
                    + " description '" + description + "'");
            ActivityDescription info = findActivityDescription(id);
            if (info != null) {
                info.thumbnail = bitmap;
                final int thumbWidth = bitmap.getWidth();
                final int thumbHeight = bitmap.getHeight();
                if ((mPortraitMode && thumbWidth > thumbHeight)
                        || (!mPortraitMode && thumbWidth < thumbHeight)) {
                    Matrix matrix = new Matrix();
                    matrix.setRotate(90.0f, (float) thumbWidth / 2, (float) thumbHeight / 2);
                    info.matrix = matrix;
                } else {
                    info.matrix = null;
                }
                mCarouselView.setTextureForItem(info.position, info.thumbnail);
            } else {
                if (DBG) Log.v(TAG, "Can't find view for id " + id);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources res = getResources();
        final View decorView = getWindow().getDecorView();

        getWindow().getDecorView().setBackgroundColor(0x80000000);
        setContentView(R.layout.recent_apps_activity);
        mCarouselView = (CarouselView)findViewById(R.id.carousel);
        mNoRecentsView = (View) findViewById(R.id.no_applications_message);
        //mCarouselView = new CarouselView(this);
        //setContentView(mCarouselView);
        mCarouselView.setSlotCount(CARD_SLOTS);
        mCarouselView.setVisibleSlots(VISIBLE_SLOTS);
        mCarouselView.createCards(1);
        mCarouselView.setStartAngle((float) -(2.0f*Math.PI * 5 / CARD_SLOTS));
        mCarouselView.setDefaultBitmap(mBlankBitmap);
        mCarouselView.setLoadingBitmap(mBlankBitmap);
        mCarouselView.setCallback(mCarouselCallback);
        mCarouselView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mPortraitMode = decorView.getHeight() > decorView.getWidth();

        refresh();


    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mPortraitMode = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        if (DBG) Log.v(TAG, "CONFIG CHANGE, mPortraitMode = " + mPortraitMode);
        refresh();
    }

    void updateRunningTasks() {
        mRunningTaskList = mActivityManager.getRunningTasks(MAX_TASKS, 0, mThumbnailReceiver);
        if (DBG) Log.v(TAG, "Portrait: " + mPortraitMode);
        for (RunningTaskInfo r : mRunningTaskList) {
            if (r.thumbnail != null) {
                int thumbWidth = r.thumbnail.getWidth();
                int thumbHeight = r.thumbnail.getHeight();
                if (DBG) Log.v(TAG, "Got thumbnail " + thumbWidth + "x" + thumbHeight);
                ActivityDescription desc = findActivityDescription(r.id);
                if (desc != null) {
                    desc.thumbnail = r.thumbnail;
                    desc.label = r.topActivity.flattenToShortString();
                    if ((mPortraitMode && thumbWidth > thumbHeight)
                            || (!mPortraitMode && thumbWidth < thumbHeight)) {
                        Matrix matrix = new Matrix();
                        matrix.setRotate(90.0f, (float) thumbWidth / 2, (float) thumbHeight / 2);
                        desc.matrix = matrix;
                    }
                } else {
                    if (DBG) Log.v(TAG, "Couldn't find ActivityDesc for id=" + r.id);
                }
            } else {
                if (DBG) Log.v(TAG, "*** RUNNING THUMBNAIL WAS NULL ***");
            }
        }
        mCarouselView.createCards(mActivityDescriptions.size());
    }

    private void updateRecentTasks() {
        final PackageManager pm = getPackageManager();
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        final List<ActivityManager.RecentTaskInfo> recentTasks =
                am.getRecentTasks(MAX_TASKS, ActivityManager.RECENT_IGNORE_UNAVAILABLE);

        ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .resolveActivityInfo(pm, 0);

        // IconUtilities iconUtilities = new IconUtilities(this); // FIXME

        int numTasks = recentTasks.size();
        mActivityDescriptions.clear();
        for (int i = 0, index = 0; i < numTasks && (index < MAX_TASKS); ++i) {
            final ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(i);

            Intent intent = new Intent(recentInfo.baseIntent);
            if (recentInfo.origActivity != null) {
                intent.setComponent(recentInfo.origActivity);
            }

            // Skip the current home activity.
            if (homeInfo != null
                    && homeInfo.packageName.equals(intent.getComponent().getPackageName())
                    && homeInfo.name.equals(intent.getComponent().getClassName())) {
                continue;
            }

            intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
            if (resolveInfo != null) {
                final ActivityInfo info = resolveInfo.activityInfo;
                final String title = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);

                int id = recentTasks.get(i).id;
                if (id != -1 && title != null && title.length() > 0 && icon != null) {
                    // icon = null; FIXME: iconUtilities.createIconDrawable(icon);
                    ActivityDescription item = new ActivityDescription(
                            null, icon, title, null, id, index);
                    item.intent = intent;
                    mActivityDescriptions.add(item);
                    if (DBG) Log.v(TAG, "Added item[" + index
                            + "], id=" + item.id
                            + ", title=" + item.label);
                    ++index;
                } else {
                    if (DBG) Log.v(TAG, "SKIPPING item " + id);
                }
            }
        }
    }

    private void refresh() {
        updateRecentTasks();
        updateRunningTasks();
        if (mActivityDescriptions.size() == 0) {
            // show "No Recent Takss"
            mNoRecentsView.setVisibility(View.VISIBLE);
            mCarouselView.setVisibility(View.GONE);
        } else {
            mNoRecentsView.setVisibility(View.GONE);
            mCarouselView.setVisibility(View.VISIBLE);
            mCarouselView.createCards(mActivityDescriptions.size());
        }
    }
}
