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

package com.google.samples.apps.iosched.provider;

import android.accounts.Account;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.google.samples.apps.iosched.provider.ScheduleContract.*;
import com.google.samples.apps.iosched.sync.ConferenceDataHandler;
import com.google.samples.apps.iosched.sync.SyncHelper;
import com.google.samples.apps.iosched.util.AccountUtils;

import static com.google.samples.apps.iosched.util.LogUtils.LOGD;
import static com.google.samples.apps.iosched.util.LogUtils.LOGI;
import static com.google.samples.apps.iosched.util.LogUtils.LOGW;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * Helper for managing {@link SQLiteDatabase} that stores data for
 * {@link ScheduleProvider}.
 */
public class ScheduleDatabase extends SQLiteOpenHelper {
    private static final String TAG = makeLogTag(ScheduleDatabase.class);

    private static final String DATABASE_NAME = "schedule.db";

    // NOTE: carefully update onUpgrade() when bumping database versions to make
    // sure user data is saved.
    private static final int VER_2015_RELEASE_B = 210;
    private static final int CUR_DATABASE_VERSION = VER_2015_RELEASE_B;

    private final Context mContext;

    interface Tables {
        String BLOCKS = "blocks";
        String TRACKS = "tracks";
        String TAGS = "tags";
        String ROOMS = "rooms";
        String SESSIONS = "sessions";
        String MY_SCHEDULE = "myschedule";
        String MY_FEEDBACK_SUBMITTED = "myfeedbacksubmitted";
        String SPEAKERS = "speakers";
        String SESSIONS_TAGS = "sessions_tags";
        String SESSIONS_SPEAKERS = "sessions_speakers";
        String SESSIONS_TRACKS = "sessions_tracks";
        String ANNOUNCEMENTS = "announcements";
        String MAPMARKERS = "mapmarkers";
        String MAPTILES = "mapoverlays";
        String HASHTAGS = "hashtags";
        String FEEDBACK = "feedback";

        String VIDEOS = "videos";

        String SESSIONS_SEARCH = "sessions_search";

        String SEARCH_SUGGEST = "search_suggest";
        String VENDORS = "vendors";

        String SESSIONS_JOIN_BLOCKS_ROOMS = "sessions "
                + "LEFT OUTER JOIN blocks ON sessions.block_id=blocks.block_id "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";

        String VENDORS_JOIN_TRACKS = "vendors "
                + "LEFT OUTER JOIN tracks ON vendors.track_id=tracks.track_id";

        String SESSIONS_SPEAKERS_JOIN_SESSIONS_BLOCKS_ROOMS = "sessions_speakers "
                + "LEFT OUTER JOIN sessions ON sessions_speakers.session_id=sessions.session_id "
                + "LEFT OUTER JOIN blocks ON sessions.block_id=blocks.block_id "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";

        String SESSIONS_TRACKS_JOIN_TRACKS = "sessions_tracks "
                + "LEFT OUTER JOIN tracks ON sessions_tracks.track_id=tracks.track_id";

        String SESSIONS_TRACKS_JOIN_SESSIONS_BLOCKS_ROOMS = "sessions_tracks "
                + "LEFT OUTER JOIN sessions ON sessions_tracks.session_id=sessions.session_id "
                + "LEFT OUTER JOIN blocks ON sessions.block_id=blocks.block_id "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";

        String SESSIONS_SEARCH_JOIN_SESSIONS_BLOCKS_ROOMS = "sessions_search "
                + "LEFT OUTER JOIN sessions ON sessions_search.session_id=sessions.session_id "
                + "LEFT OUTER JOIN blocks ON sessions.block_id=blocks.block_id "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";

        String SESSIONS_JOIN_TRACKS_JOIN_BLOCKS = "sessions "
                + "LEFT OUTER JOIN sessions_tracks ON "
                + "sessions_tracks.session_id=sessions.session_id "
                + "LEFT OUTER JOIN tracks ON tracks.track_id=sessions_tracks.track_id "
                + "LEFT OUTER JOIN blocks ON sessions.block_id=blocks.block_id";

        String BLOCKS_JOIN_SESSIONS = "blocks "
                + "LEFT OUTER JOIN sessions ON blocks.block_id=sessions.block_id";

        String SESSIONS_JOIN_MYSCHEDULE = "sessions "
                + "LEFT OUTER JOIN myschedule ON sessions.session_id=myschedule.session_id "
                + "AND myschedule.account_name=? ";

        String SESSIONS_JOIN_ROOMS_TAGS = "sessions "
                + "LEFT OUTER JOIN myschedule ON sessions.session_id=myschedule.session_id "
                + "AND myschedule.account_name=? "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id "
                + "LEFT OUTER JOIN sessions_tags ON sessions.session_id=sessions_tags.session_id";

        String SESSIONS_JOIN_ROOMS_TAGS_FEEDBACK_MYSCHEDULE = "sessions "
                + "LEFT OUTER JOIN myschedule ON sessions.session_id=myschedule.session_id "
                + "AND myschedule.account_name=? "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id "
                + "LEFT OUTER JOIN sessions_tags ON sessions.session_id=sessions_tags.session_id "
                + "LEFT OUTER JOIN feedback ON sessions.session_id=feedback.session_id";

        String SESSIONS_JOIN_ROOMS = "sessions "
                + "LEFT OUTER JOIN myschedule ON sessions.session_id=myschedule.session_id "
                + "AND myschedule.account_name=? "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";

        String SESSIONS_SPEAKERS_JOIN_SPEAKERS = "sessions_speakers "
                + "LEFT OUTER JOIN speakers ON sessions_speakers.speaker_id=speakers.speaker_id";

        String SESSIONS_TAGS_JOIN_TAGS = "sessions_tags "
                + "LEFT OUTER JOIN tags ON sessions_tags.tag_name=tags.tag_name";

        String SESSIONS_SPEAKERS_JOIN_SESSIONS_ROOMS = "sessions_speakers "
                + "LEFT OUTER JOIN sessions ON sessions_speakers.session_id=sessions.session_id "
                + "LEFT OUTER JOIN rooms ON sessions.room_id=rooms.room_id";
    }

    private interface Triggers {
        // Deletes from dependent tables when corresponding sessions are deleted.
        String SESSIONS_TAGS_DELETE = "sessions_tags_delete";
        String SESSIONS_SPEAKERS_DELETE = "sessions_speakers_delete";
        String SESSIONS_MY_SCHEDULE_DELETE = "sessions_myschedule_delete";
        String SESSIONS_FEEDBACK_DELETE = "sessions_feedback_delete";
        String SESSIONS_SEARCH_INSERT = "sessions_search_insert";
        String SESSIONS_SEARCH_DELETE = "sessions_search_delete";
        String SESSIONS_SEARCH_UPDATE = "sessions_search_update";
        String SESSIONS_TRACKS_DELETE = "sessions_tracks_delete";
    }


    public interface SessionsSpeakers {
        String SESSION_ID = "session_id";
        String SPEAKER_ID = "speaker_id";
    }

    public interface SessionsTags {
        String SESSION_ID = "session_id";
        String TAG_ID = "tag_name";
    }

    interface SessionsSearchColumns {
        String SESSION_ID = "session_id";
        String BODY = "body";
    }

    public interface SessionsTracks {
        String SESSION_ID = "session_id";
        String TRACK_ID = "track_id";
    }

    /**
     * Fully-qualified field names.
     */
    private interface Qualified {
        String SESSIONS_SEARCH = Tables.SESSIONS_SEARCH + "(" + SessionsSearchColumns.SESSION_ID
                + "," + SessionsSearchColumns.BODY + ")";

        String SESSIONS_TAGS_SESSION_ID = Tables.SESSIONS_TAGS + "."
                + SessionsTags.SESSION_ID;

        String SESSIONS_SPEAKERS_SESSION_ID = Tables.SESSIONS_SPEAKERS + "."
                + SessionsSpeakers.SESSION_ID;

        String SESSIONS_SPEAKERS_SPEAKER_ID = Tables.SESSIONS_SPEAKERS + "."
                + SessionsSpeakers.SPEAKER_ID;

        String SPEAKERS_SPEAKER_ID = Tables.SPEAKERS + "." + Speakers.SPEAKER_ID;

        String FEEDBACK_SESSION_ID = Tables.FEEDBACK + "." + FeedbackColumns.SESSION_ID;

        String SESSIONS_SEARCH_SESSION_ID = Tables.SESSIONS_SEARCH + "."
                + SessionsSearchColumns.SESSION_ID;

        String SESSIONS_TRACKS_SESSION_ID = Tables.SESSIONS_TRACKS + "."
                + SessionsTracks.SESSION_ID;
    }

    /**
     * {@code REFERENCES} clauses.
     */
    private interface References {
        String BLOCK_ID = "REFERENCES " + Tables.BLOCKS + "(" + Blocks.BLOCK_ID + ")";
        String TRACK_ID = "REFERENCES " + Tables.TRACKS + "(" + Tracks.TRACK_ID + ")";
        String TAG_ID = "REFERENCES " + Tables.TAGS + "(" + Tags.TAG_NAME + ")";
        String ROOM_ID = "REFERENCES " + Tables.ROOMS + "(" + Rooms.ROOM_ID + ")";
        String SESSION_ID = "REFERENCES " + Tables.SESSIONS + "(" + Sessions.SESSION_ID + ")";
        String VIDEO_ID = "REFERENCES " + Tables.VIDEOS + "(" + Videos.VIDEO_ID + ")";
        String SPEAKER_ID = "REFERENCES " + Tables.SPEAKERS + "(" + Speakers.SPEAKER_ID + ")";
        String VENDOR_ID = "REFERENCES " + Tables.VENDORS + "(" + Vendors.VENDOR_ID + ")";
    }

    public ScheduleDatabase(Context context) {
        super(context, DATABASE_NAME, null, CUR_DATABASE_VERSION);
        mContext = context;
    }

    private interface Subquery {
        /**
         * Subquery used to build the {@link SessionsSearchColumns#BODY} string
         * used for indexing {@link Sessions} content.
         */
        String SESSIONS_BODY = "(new." + Sessions.SESSION_TITLE
                + "||'; '||new." + Sessions.SESSION_ABSTRACT
                + "||'; '||" + "coalesce(new." + Sessions.SESSION_TAGS + ", '')"
                + ")";
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.BLOCKS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + BlocksColumns.BLOCK_ID + " TEXT NOT NULL,"
                + BlocksColumns.BLOCK_TITLE + " TEXT NOT NULL,"
                + BlocksColumns.BLOCK_START + " INTEGER NOT NULL,"
                + BlocksColumns.BLOCK_END + " INTEGER NOT NULL,"
                + BlocksColumns.BLOCK_TYPE + " TEXT,"
                + BlocksColumns.BLOCK_SUBTITLE + " TEXT,"
                + BlocksColumns.BLOCK_META + " TEXT,"
                + "UNIQUE (" + BlocksColumns.BLOCK_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.TRACKS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TracksColumns.TRACK_ID + " TEXT NOT NULL,"
                + TracksColumns.TRACK_NAME + " TEXT,"
                + TracksColumns.TRACK_COLOR + " INTEGER,"
                + TracksColumns.TRACK_ABSTRACT + " TEXT,"
                + "UNIQUE (" + TracksColumns.TRACK_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.TAGS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TagsColumns.TAG_NAME + " TEXT NOT NULL,"
                + TagsColumns.TAG_CATEGORY + " TEXT NOT NULL,"
                + TagsColumns.TAG_ORDER_IN_CATEGORY + " INTEGER,"
                + TagsColumns.TAG_COLOR + " TEXT,"
                + TagsColumns.TAG_ABSTRACT + " TEXT,"
                + "UNIQUE (" + TagsColumns.TAG_NAME + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.ROOMS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + RoomsColumns.ROOM_ID + " TEXT NOT NULL,"
                + RoomsColumns.ROOM_NAME + " TEXT,"
                + RoomsColumns.ROOM_FLOOR + " TEXT,"
                + "UNIQUE (" + RoomsColumns.ROOM_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SESSIONS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncColumns.UPDATED + " INTEGER NOT NULL,"
                + SessionsColumns.SESSION_ID + " TEXT NOT NULL,"
                + Sessions.BLOCK_ID + " TEXT " + References.BLOCK_ID + ","
                + Sessions.ROOM_ID + " TEXT " + References.ROOM_ID + ","
                + SessionsColumns.SESSION_START + " INTEGER NOT NULL,"
                + SessionsColumns.SESSION_END + " INTEGER NOT NULL,"
                + SessionsColumns.SESSION_TYPE + " TEXT,"
                + SessionsColumns.SESSION_LEVEL + " TEXT,"
                + SessionsColumns.SESSION_TITLE + " TEXT,"
                + SessionsColumns.SESSION_ABSTRACT + " TEXT,"
                + SessionsColumns.SESSION_REQUIREMENTS + " TEXT,"
                + SessionsColumns.SESSION_IN_MY_SCHEDULE + " TEXT, "
                + SessionsColumns.SESSION_KEYWORDS + " TEXT,"
                + SessionsColumns.SESSION_HASHTAG + " TEXT,"
                + SessionsColumns.SESSION_URL + " TEXT,"
                + SessionsColumns.SESSION_YOUTUBE_URL + " TEXT,"
                + SessionsColumns.SESSION_MODERATOR_URL + " TEXT,"
                + SessionsColumns.SESSION_PDF_URL + " TEXT,"
                + SessionsColumns.SESSION_NOTES_URL + " TEXT,"
                + SessionsColumns.SESSION_STARRED + " INTEGER NOT NULL DEFAULT 0,"
                + SessionsColumns.SESSION_CAL_EVENT_ID + " INTEGER,"
                + SessionsColumns.SESSION_LIVESTREAM_ID + " TEXT,"
                + SessionsColumns.SESSION_TAGS + " TEXT,"
                + SessionsColumns.SESSION_GROUPING_ORDER + " INTEGER,"
                + SessionsColumns.SESSION_SPEAKER_NAMES + " TEXT,"
                + SessionsColumns.SESSION_IMPORT_HASHCODE + " TEXT NOT NULL DEFAULT '',"
                + SessionsColumns.SESSION_MAIN_TAG + " TEXT,"
                + SessionsColumns.SESSION_COLOR + " INTEGER,"
                + SessionsColumns.SESSION_CAPTIONS_URL + " TEXT,"
                + SessionsColumns.SESSION_PHOTO_URL + " TEXT,"
                + SessionsColumns.SESSION_RELATED_CONTENT + " TEXT,"
                + "UNIQUE (" + SessionsColumns.SESSION_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SPEAKERS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncColumns.UPDATED + " INTEGER NOT NULL,"
                + SpeakersColumns.SPEAKER_ID + " TEXT NOT NULL,"
                + SpeakersColumns.SPEAKER_NAME + " TEXT,"
                + SpeakersColumns.SPEAKER_IMAGE_URL + " TEXT,"
                + SpeakersColumns.SPEAKER_COMPANY + " TEXT,"
                + SpeakersColumns.SPEAKER_ABSTRACT + " TEXT,"
                + SpeakersColumns.SPEAKER_URL + " TEXT,"
                + "UNIQUE (" + SpeakersColumns.SPEAKER_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.MY_SCHEDULE + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + MySchedule.SESSION_ID + " TEXT NOT NULL " + References.SESSION_ID + ","
                + MySchedule.MY_SCHEDULE_ACCOUNT_NAME + " TEXT NOT NULL,"
                + MySchedule.MY_SCHEDULE_DIRTY_FLAG + " INTEGER NOT NULL DEFAULT 1,"
                + MySchedule.MY_SCHEDULE_IN_SCHEDULE + " INTEGER NOT NULL DEFAULT 1,"
                + "UNIQUE (" + MySchedule.SESSION_ID + ","
                + MySchedule.MY_SCHEDULE_ACCOUNT_NAME + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SESSIONS_SPEAKERS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SessionsSpeakers.SESSION_ID + " TEXT NOT NULL " + References.SESSION_ID + ","
                + SessionsSpeakers.SPEAKER_ID + " TEXT NOT NULL " + References.SPEAKER_ID + ","
                + "UNIQUE (" + SessionsSpeakers.SESSION_ID + ","
                + SessionsSpeakers.SPEAKER_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SESSIONS_TAGS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SessionsTags.SESSION_ID + " TEXT NOT NULL " + References.SESSION_ID + ","
                + SessionsTags.TAG_ID + " TEXT NOT NULL " + References.TAG_ID + ","
                + "UNIQUE (" + SessionsTags.SESSION_ID + ","
                + SessionsTags.TAG_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SESSIONS_TRACKS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SessionsTracks.SESSION_ID + " TEXT NOT NULL " + References.SESSION_ID + ","
                + SessionsTracks.TRACK_ID + " TEXT NOT NULL " + References.TRACK_ID
                + ")");

        db.execSQL("CREATE TABLE " + Tables.VENDORS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncColumns.UPDATED + " INTEGER NOT NULL,"
                + VendorsColumns.VENDOR_ID + " TEXT NOT NULL,"
                + Vendors.TRACK_ID + " TEXT " + References.TRACK_ID + ","
                + VendorsColumns.VENDOR_NAME + " TEXT,"
                + VendorsColumns.VENDOR_LOCATION + " TEXT,"
                + VendorsColumns.VENDOR_DESC + " TEXT,"
                + VendorsColumns.VENDOR_URL + " TEXT,"
                + VendorsColumns.VENDOR_PRODUCT_DESC + " TEXT,"
                + VendorsColumns.VENDOR_LOGO_URL + " TEXT,"
                + VendorsColumns.VENDOR_STARRED + " INTEGER,"
                + "UNIQUE (" + VendorsColumns.VENDOR_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.ANNOUNCEMENTS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncColumns.UPDATED + " INTEGER NOT NULL,"
                + AnnouncementsColumns.ANNOUNCEMENT_ID + " TEXT,"
                + AnnouncementsColumns.ANNOUNCEMENT_TITLE + " TEXT NOT NULL,"
                + AnnouncementsColumns.ANNOUNCEMENT_SUMMARY + " TEXT,"
                + AnnouncementsColumns.ANNOUNCEMENT_ACTIVITY_JSON + " BLOB,"
                + AnnouncementsColumns.ANNOUNCEMENT_URL + " TEXT,"
                + AnnouncementsColumns.ANNOUNCEMENT_DATE + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + Tables.MAPTILES + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + MapTileColumns.TILE_FLOOR + " INTEGER NOT NULL,"
                + MapTileColumns.TILE_FILE + " TEXT NOT NULL,"
                + MapTileColumns.TILE_URL + " TEXT NOT NULL,"
                + "UNIQUE (" + MapTileColumns.TILE_FLOOR + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.FEEDBACK + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncColumns.UPDATED + " INTEGER NOT NULL,"
                + Sessions.SESSION_ID + " TEXT " + References.SESSION_ID + ","
                + FeedbackColumns.SESSION_RATING + " INTEGER NOT NULL,"
                + FeedbackColumns.ANSWER_RELEVANCE + " INTEGER NOT NULL,"
                + FeedbackColumns.ANSWER_CONTENT + " INTEGER NOT NULL,"
                + FeedbackColumns.ANSWER_SPEAKER + " INTEGER NOT NULL,"
                + FeedbackColumns.COMMENTS + " TEXT,"
                + FeedbackColumns.SYNCED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_FEEDBACK_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.FEEDBACK + " "
                + " WHERE " + Qualified.FEEDBACK_SESSION_ID + "=old." + Sessions.SESSION_ID
                + ";" + " END;");

        db.execSQL("CREATE TABLE " + Tables.MAPMARKERS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + MapMarkerColumns.MARKER_ID + " TEXT NOT NULL,"
                + MapMarkerColumns.MARKER_TYPE + " TEXT NOT NULL,"
                + MapMarkerColumns.MARKER_LATITUDE + " DOUBLE NOT NULL,"
                + MapMarkerColumns.MARKER_LONGITUDE + " DOUBLE NOT NULL,"
                + MapMarkerColumns.MARKER_LABEL + " TEXT,"
                + MapMarkerColumns.MARKER_FLOOR + " INTEGER NOT NULL,"
                + "UNIQUE (" + MapMarkerColumns.MARKER_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.HASHTAGS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + HashtagColumns.HASHTAG_NAME + " TEXT NOT NULL,"
                + HashtagColumns.HASHTAG_DESCRIPTION + " TEXT NOT NULL,"
                + HashtagColumns.HASHTAG_COLOR + " INTEGER NOT NULL,"
                + HashtagColumns.HASHTAG_ORDER + " INTEGER NOT NULL,"
                + "UNIQUE (" + HashtagColumns.HASHTAG_NAME + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.VIDEOS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + VideoColumns.VIDEO_ID + " TEXT NOT NULL,"
                + VideoColumns.VIDEO_YEAR + " INTEGER NOT NULL,"
                + VideoColumns.VIDEO_TITLE + " TEXT,"
                + VideoColumns.VIDEO_DESC + " TEXT,"
                + VideoColumns.VIDEO_VID + " TEXT,"
                + VideoColumns.VIDEO_TOPIC + " TEXT,"
                + VideoColumns.VIDEO_SPEAKERS + " TEXT,"
                + VideoColumns.VIDEO_THUMBNAIL_URL + " TEXT,"
                + VideoColumns.VIDEO_IMPORT_HASHCODE + " TEXT NOT NULL,"
                + "UNIQUE (" + VideoColumns.VIDEO_ID + ") ON CONFLICT REPLACE)");

        createSessionsSearch(db);
        createSessionsDeleteTriggers(db);

        // Search suggestions
        db.execSQL("CREATE TABLE " + Tables.SEARCH_SUGGEST + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SearchManager.SUGGEST_COLUMN_TEXT_1 + " TEXT NOT NULL)");
    }

    private static void createSessionsSearch(SQLiteDatabase db) {
        // Full-text search index. Update using updateSessionSearchIndex method.
        // Use the porter tokenizer for simple stemming, so that "frustration" matches "frustrated."
        db.execSQL("CREATE VIRTUAL TABLE " + Tables.SESSIONS_SEARCH + " USING fts3("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SessionsSearchColumns.BODY + " TEXT NOT NULL,"
                + SessionsSearchColumns.SESSION_ID
                + " TEXT NOT NULL " + References.SESSION_ID + ","
                + "UNIQUE (" + SessionsSearchColumns.SESSION_ID + ") ON CONFLICT REPLACE,"
                + "tokenize=porter)");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_SEARCH_INSERT + " AFTER INSERT ON "
                + Tables.SESSIONS + " BEGIN INSERT INTO " + Qualified.SESSIONS_SEARCH + " "
                + " VALUES(new." + Sessions.SESSION_ID + ", " + Subquery.SESSIONS_BODY + ");"
                + " END;");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_SEARCH_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.SESSIONS_SEARCH + " "
                + " WHERE " + Qualified.SESSIONS_SEARCH_SESSION_ID + "=old." + Sessions.SESSION_ID
                + ";" + " END;");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_SEARCH_UPDATE
                + " AFTER UPDATE ON " + Tables.SESSIONS
                + " BEGIN UPDATE sessions_search SET " + SessionsSearchColumns.BODY + " = "
                + Subquery.SESSIONS_BODY + " WHERE session_id = old.session_id"
                + "; END;");

    }

    private static void createSessionsDeleteTriggers(SQLiteDatabase db) {
        // Session deletion triggers
        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_TRACKS_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.SESSIONS_TRACKS + " "
                + " WHERE " + Qualified.SESSIONS_TRACKS_SESSION_ID + "=old." + Sessions.SESSION_ID
                + ";" + " END;");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_TAGS_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.SESSIONS_TAGS + " "
                + " WHERE " + Qualified.SESSIONS_TAGS_SESSION_ID + "=old." + Sessions.SESSION_ID
                + ";" + " END;");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_SPEAKERS_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.SESSIONS_SPEAKERS + " "
                + " WHERE " + Qualified.SESSIONS_SPEAKERS_SESSION_ID + "=old." + Sessions.SESSION_ID
                + ";" + " END;");

        db.execSQL("CREATE TRIGGER " + Triggers.SESSIONS_MY_SCHEDULE_DELETE + " AFTER DELETE ON "
                + Tables.SESSIONS + " BEGIN DELETE FROM " + Tables.MY_SCHEDULE + " "
                + " WHERE " + Tables.MY_SCHEDULE + "." + MySchedule.SESSION_ID +
                "=old." + Sessions.SESSION_ID
                + ";" + " END;");
    }

    /**
     * Updates the session search index. This should be done sparingly, as the queries are rather
     * complex.
     */
    static void updateSessionSearchIndex(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + Tables.SESSIONS_SEARCH);

        db.execSQL("INSERT INTO " + Qualified.SESSIONS_SEARCH
                + " SELECT s." + Sessions.SESSION_ID + ",("

                // Full text body
                + Sessions.SESSION_TITLE + "||'; '||"
                + Sessions.SESSION_ABSTRACT + "||'; '||"
                + "IFNULL(GROUP_CONCAT(t." + Speakers.SPEAKER_NAME + ",' '),'')||'; '||"
                + "'')"

                + " FROM " + Tables.SESSIONS + " s "
                + " LEFT OUTER JOIN"

                // Subquery resulting in session_id, speaker_id, speaker_name
                + "(SELECT " + Sessions.SESSION_ID + "," + Qualified.SPEAKERS_SPEAKER_ID
                + "," + Speakers.SPEAKER_NAME
                + " FROM " + Tables.SESSIONS_SPEAKERS
                + " INNER JOIN " + Tables.SPEAKERS
                + " ON " + Qualified.SESSIONS_SPEAKERS_SPEAKER_ID + "="
                + Qualified.SPEAKERS_SPEAKER_ID
                + ") t"

                // Grand finale
                + " ON s." + Sessions.SESSION_ID + "=t." + Sessions.SESSION_ID
                + " GROUP BY s." + Sessions.SESSION_ID);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LOGD(TAG, "onUpgrade() from " + oldVersion + " to " + newVersion);

        // Cancel any sync currently in progress
        Account account = AccountUtils.getActiveAccount(mContext);
        if (account != null) {
            LOGI(TAG, "Cancelling any pending syncs for account");
            ContentResolver.cancelSync(account, ScheduleContract.CONTENT_AUTHORITY);
        }

        // Current DB version. We update this variable as we perform upgrades to reflect
        // the current version we are in.
        int version = oldVersion;

        // Indicates whether the data we currently have should be invalidated as a
        // result of the db upgrade. Default is true (invalidate); if we detect that this
        // is a trivial DB upgrade, we set this to false.
        boolean dataInvalidated = true;

        LOGD(TAG, "After upgrade logic, at version " + version);

        // At this point, we ran out of upgrade logic, so if we are still at the wrong
        // version, we have no choice but to delete everything and create everything again.
        if (version != CUR_DATABASE_VERSION) {
            LOGW(TAG, "Upgrade unsuccessful -- destroying old data during upgrade");

            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_TAGS_DELETE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_SPEAKERS_DELETE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_FEEDBACK_DELETE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_MY_SCHEDULE_DELETE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_SEARCH_INSERT);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_SEARCH_DELETE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_SEARCH_UPDATE);
            db.execSQL("DROP TRIGGER IF EXISTS " + Triggers.SESSIONS_TRACKS_DELETE);

            db.execSQL("DROP TABLE IF EXISTS " + Tables.BLOCKS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.TRACKS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.ROOMS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.TAGS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SESSIONS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SPEAKERS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.MY_SCHEDULE);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.MY_FEEDBACK_SUBMITTED);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SESSIONS_SPEAKERS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SESSIONS_TRACKS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SESSIONS_TAGS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.VENDORS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.ANNOUNCEMENTS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.FEEDBACK);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SESSIONS_SEARCH);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SEARCH_SUGGEST);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.MAPMARKERS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.MAPTILES);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.HASHTAGS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.VIDEOS);

            onCreate(db);
            version = CUR_DATABASE_VERSION;
        }

        if (dataInvalidated) {
            LOGD(TAG, "Data invalidated; resetting our data timestamp.");
            ConferenceDataHandler.resetDataTimestamp(mContext);
            if (account != null) {
                LOGI(TAG, "DB upgrade complete. Requesting resync.");
                SyncHelper.requestManualSync(account);
            }
        }
    }

    public static void deleteDatabase(Context context) {
        context.deleteDatabase(DATABASE_NAME);
    }
}
