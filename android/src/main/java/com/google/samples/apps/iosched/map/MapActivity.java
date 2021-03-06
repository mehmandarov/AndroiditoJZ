/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.google.samples.apps.iosched.map;

import no.java.schedule.v2.R;
import com.google.samples.apps.iosched.provider.ScheduleContract;
import com.google.samples.apps.iosched.session.SessionDetailConstants;
import com.google.samples.apps.iosched.ui.BaseActivity;
import com.google.samples.apps.iosched.util.AnalyticsHelper;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * Activity that displays a {@link com.google.samples.apps.iosched.map.MapFragment} and a {@link
 * com.google.samples.apps.iosched.map.MapInfoFragment}.
 * Supports 'detached' mode, where the toolbar contains an up navigation option that finishes this
 * Activity. (see {@link #EXTRA_DETACHED_MODE}
 * Optionally a room can be specified via {@link #EXTRA_ROOM} that pans the map to its indicated
 * marker.
 *
 * @see com.google.samples.apps.iosched.map.MapInfoFragment#newInstace(android.content.Context)
 */
public class MapActivity extends BaseActivity
        implements SlideableInfoFragment.Callback, MapFragment.Callbacks {

    private static final String TAG = makeLogTag(MapActivity.class);

    private static final String SCREEN_LABEL = "Map";

    /**
     * When specified, will automatically point the map to the requested room.
     */
    public static final String EXTRA_ROOM = "com.google.android.iosched.extra.ROOM";

    public static final String EXTRA_DETACHED_MODE
            = "com.google.samples.apps.iosched.EXTRA_DETACHED_MODE";

    public static final String BUNDLE_STATE_MAPVIEW = "mapview";

    public static final int DEFAULT_BUTTON_COLOR = R.color.jz_yellow;
    public static final int DEFAULT_BUTTON_SELECTED_COLOR = android.R.color.darker_gray;

    private boolean mDetachedMode;

    private MapFragment mMapFragment;

    private MapInfoFragment mInfoFragment;

    public Button mFloor1Button;
    public Button mFloor2Button;
    public Button mFloor3Button;
    public Button mFloorAllButton;

    private View mInfoContainer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentManager fm = getFragmentManager();
        mMapFragment = (MapFragment) fm.findFragmentByTag("map");

        mDetachedMode = getIntent().getBooleanExtra(EXTRA_DETACHED_MODE, false);

        if (isFinishing()) {
            return;
        }

        setContentView(R.layout.map_act);
        mInfoContainer = findViewById(R.id.map_detail_popup);
        mFloorAllButton = (Button)findViewById(R.id.allfloors_button);
        mFloor1Button = (Button)findViewById(R.id.floor1_button);
        mFloor2Button = (Button)findViewById(R.id.floor2_button);
        mFloor3Button = (Button) findViewById(R.id.floor3_button);

        mFloorAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapFragment.showAllFloors(true);
                mFloorAllButton.setBackgroundColor(ContextCompat.getColor(MapActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor2Button);
                ResetColorButton(mFloor1Button);
            }
        });

        mFloor1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapFragment.showAllFloors(false);
                mMapFragment.showMarkersForSpecificFloor(0);
                mFloor1Button.setBackgroundColor(ContextCompat.getColor(MapActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor2Button);
            }
        });

        mFloor2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapFragment.showAllFloors(false);
                mMapFragment.showMarkersForSpecificFloor(1);
                mFloor2Button.setBackgroundColor(ContextCompat.getColor(MapActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor3Button);
                ResetColorButton(mFloor1Button);

            }
        });

        mFloor3Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapFragment.showAllFloors(false);
                mMapFragment.showMarkersForSpecificFloor(2);
                mFloor3Button.setBackgroundColor(ContextCompat.getColor(MapActivity.this, DEFAULT_BUTTON_SELECTED_COLOR));
                ResetColorButton(mFloorAllButton);
                ResetColorButton(mFloor2Button);
                ResetColorButton(mFloor1Button);

            }
        });


        // ANALYTICS SCREEN: View the Map screen on a phone
        // Contains: Nothing (Page name is a constant)
        AnalyticsHelper.sendScreenView(SCREEN_LABEL);

        overridePendingTransition(0, 0);
    }

    private void ResetColorButton(Button button) {
        button.setBackgroundColor(ContextCompat.getColor(MapActivity.this, DEFAULT_BUTTON_COLOR));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save the mapview state in a separate bundle parameter
        final Bundle mapviewState = new Bundle();
        mMapFragment.onSaveInstanceState(mapviewState);
        outState.putBundle(BUNDLE_STATE_MAPVIEW, mapviewState);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDetachedMode) {
            final Toolbar toolbar = getActionBarToolbar();
            toolbar.setNavigationIcon(R.drawable.ic_up);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });
        }

        if (mMapFragment == null) {
            // Either restore the state of the map or read it from the Intent extras.
            if (savedInstanceState != null) {
                // Restore state from existing bundle
                Bundle previousState = savedInstanceState.getBundle(BUNDLE_STATE_MAPVIEW);
                mMapFragment = MapFragment.newInstance(previousState);
            } else {
                // Get highlight room id (if specified in intent extras)
                final String highlightRoomName = getIntent().hasExtra(EXTRA_ROOM) ? getIntent()
                        .getExtras().getString(EXTRA_ROOM) : null;
                mMapFragment = MapFragment.newInstance(highlightRoomName);
            }
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container_map, mMapFragment, "map")
                    .commit();
        }
        if (mInfoFragment == null) {
            mInfoFragment = MapInfoFragment.newInstace(this);
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container_map_info, mInfoFragment, "mapsheet")
                    .commit();
        }

        mDetachedMode = getIntent().getBooleanExtra(EXTRA_DETACHED_MODE, false);
    }

    @Override
    public void onBackPressed() {
        if (mInfoFragment != null && mInfoFragment.isExpanded()) {
            mInfoFragment.minimize();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected int getSelfNavDrawerItem() {
        return mDetachedMode ? NAVDRAWER_ITEM_INVALID : NAVDRAWER_ITEM_MAP;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getIntent().getBooleanExtra(EXTRA_DETACHED_MODE, false)
                && item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Adjust the padding in the map fragment when the info fragment has been resized.
     * The size is self reported from the fragment and has to be adjusted before it can be
     * applied to the map.
     *
     * For {@link com.google.samples.apps.iosched.map.InlineInfoFragment} (that is only displayed
     * on the left of the screen), the full extend of its container layout (including padding) is
     * passed to the map fragment.
     * For {@link com.google.samples.apps.iosched.map.SlideableInfoFragment} (that is only
     * displayed
     * at the bottom of the screen), its actual height is passed through to the map.
     */
    @Override
    public void onInfoSizeChanged(int left, int top, int right, int bottom) {
        if (mMapFragment != null) {
            if (mInfoFragment instanceof InlineInfoFragment) {
                // InlineInfoFragment is shown on the left on tablet layouts.
                // Use the right edge of its containers for padding of the map.
                //TODO: RTL support - compare left and right positioning
                if (right > 0) {
                    mMapFragment.setMapInsets(mInfoContainer.getRight(), 0, 0, 0);
                } else {
                    mMapFragment.setMapInsets(0, 0, 0, 0);
                }
            } else if (mInfoFragment instanceof SlideableInfoFragment) {
                // SlideableInfoFragment is only shown on phone layouts at the bottom of the screen,
                // but only up to 50% of the total height of the screen
                if ((float) top / (float) bottom > SlideableInfoFragment.MAX_PANEL_PADDING_FACTOR) {

                    mMapFragment.setMapInsets(0, 0, 0, bottom - top);
                }
                final int bottomPadding = Math.min(bottom - top,
                        Math.round(bottom * SlideableInfoFragment.MAX_PANEL_PADDING_FACTOR));
                mMapFragment.setMapInsets(0, 0, 0, bottomPadding);
            }
            // F
        }
    }

    @Override
    public void onSessionClicked(String sessionId) {
        // ANALYTICS EVENT: Click on a session in the Maps screen.
        // Contains: The session ID.
        AnalyticsHelper.sendEvent(SCREEN_LABEL, "selectsession", sessionId);

        getLUtils().startActivityWithTransition(
                new Intent(Intent.ACTION_VIEW,
                        ScheduleContract.Sessions.buildSessionUri(sessionId)),
                null,
                SessionDetailConstants.TRANSITION_NAME_PHOTO
        );
    }

    @Override
    public void onInfoShowOsloSpektrum() {
        if (mInfoFragment != null) {
            mInfoFragment.showOsloSpektrum();
        }
        setTabletInfoVisibility(View.VISIBLE);

    }

    @Override
    public void onInfoShowTitle(String label, int icon) {
        if (mInfoFragment != null) {
            mInfoFragment.showTitleOnly(icon, label);
        }
        setTabletInfoVisibility(View.VISIBLE);

    }

    @Override
    public void onInfoShowSessionlist(String roomId, String roomTitle, int roomType) {
        if (mInfoFragment != null) {
            mInfoFragment.showSessionList(roomId, roomTitle, roomType);
        }
        setTabletInfoVisibility(View.VISIBLE);

    }

    @Override
    public void onInfoShowFirstSessionTitle(String roomId, String roomTitle, int roomType) {
        if (mInfoFragment != null) {
            mInfoFragment.showFirstSessionTitle(roomId, roomTitle, roomType);
        }
        setTabletInfoVisibility(View.VISIBLE);
    }

    @Override
    public void onInfoHide() {
        if (mInfoFragment != null) {
            mInfoFragment.hide();
        }
        setTabletInfoVisibility(View.GONE);
    }

    private void setTabletInfoVisibility(int visibility) {
        final View view = findViewById(R.id.map_detail_popup);
        if (view != null) {
            view.setVisibility(visibility);

        }
    }
}
