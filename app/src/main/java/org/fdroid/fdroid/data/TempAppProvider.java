package org.fdroid.fdroid.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import org.fdroid.fdroid.data.Schema.ApkTable;
import org.fdroid.fdroid.data.Schema.AppTable;

/**
 * This class does all of its operations in a temporary sqlite table.
 */
public class TempAppProvider extends AppProvider {

    /**
     * The name of the in memory database used for updating.
     */
    static final String DB = "temp_update_db";

    private static final String PROVIDER_NAME = "TempAppProvider";

    private static final String TABLE_TEMP_APP = "temp_" + AppTable.NAME;

    private static final String PATH_INIT = "init";
    private static final String PATH_COMMIT = "commit";

    private static final int CODE_INIT = 10000;
    private static final int CODE_COMMIT = CODE_INIT + 1;

    private static final UriMatcher MATCHER = new UriMatcher(-1);

    static {
        MATCHER.addURI(getAuthority(), PATH_INIT, CODE_INIT);
        MATCHER.addURI(getAuthority(), PATH_COMMIT, CODE_COMMIT);
        MATCHER.addURI(getAuthority(), "*", CODE_SINGLE);
    }

    @Override
    protected String getTableName() {
        return TABLE_TEMP_APP;
    }

    public static String getAuthority() {
        return AUTHORITY + "." + PROVIDER_NAME;
    }

    public static Uri getContentUri() {
        return Uri.parse("content://" + getAuthority());
    }

    public static Uri getAppUri(App app) {
        return Uri.withAppendedPath(getContentUri(), app.packageName);
    }

    public static class Helper {

        /**
         * Deletes the old temporary table (if it exists). Then creates a new temporary apk provider
         * table and populates it with all the data from the real apk provider table.
         */
        public static void init(Context context) {
            Uri uri = Uri.withAppendedPath(getContentUri(), PATH_INIT);
            context.getContentResolver().insert(uri, new ContentValues());
            TempApkProvider.Helper.init(context);
        }

        /**
         * Saves data from the temp table to the apk table, by removing _EVERYTHING_ from the real
         * apk table and inserting all of the records from here. The temporary table is then removed.
         */
        public static void commitAppsAndApks(Context context) {
            Uri uri = Uri.withAppendedPath(getContentUri(), PATH_COMMIT);
            context.getContentResolver().insert(uri, new ContentValues());
        }
    }

    @Override
    protected String getApkTableName() {
        return TempApkProvider.TABLE_TEMP_APK;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        switch (MATCHER.match(uri)) {
            case CODE_INIT:
                initTable();
                return null;
            case CODE_COMMIT:
                updateAppDetails();
                commitTable();
                return null;
            default:
                return super.insert(uri, values);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        QuerySelection query = new QuerySelection(where, whereArgs);
        switch (MATCHER.match(uri)) {
            case CODE_SINGLE:
                query = query.add(querySingle(uri.getLastPathSegment()));
                break;

            default:
                throw new UnsupportedOperationException("Update not supported for " + uri + ".");
        }

        int count = db().update(getTableName(), values, query.getSelection(), query.getArgs());
        if (!isApplyingBatch()) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    private void ensureTempTableDetached(SQLiteDatabase db) {
        try {
            db.execSQL("DETACH DATABASE " + DB);
        } catch (SQLiteException e) {
            // We expect that most of the time the database will not exist unless an error occurred
            // midway through the last update, The resulting exception is:
            //   android.database.sqlite.SQLiteException: no such database: temp_update_db (code 1)
        }
    }

    private void initTable() {
        final SQLiteDatabase db = db();
        ensureTempTableDetached(db);
        db.execSQL("ATTACH DATABASE ':memory:' AS " + DB);
        db.execSQL("CREATE TABLE " + DB + "." + getTableName() + " AS SELECT * FROM main." + AppTable.NAME);
        db.execSQL("CREATE INDEX IF NOT EXISTS " + DB + ".app_id ON " + getTableName() + " (id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS " + DB + ".app_upstreamVercode ON " + getTableName() + " (upstreamVercode);");
        db.execSQL("CREATE INDEX IF NOT EXISTS " + DB + ".app_compatible ON " + getTableName() + " (compatible);");
    }

    private void commitTable() {
        final SQLiteDatabase db = db();
        try {
            db.beginTransaction();

            final String tempApp = DB + "." + TempAppProvider.TABLE_TEMP_APP;
            final String tempApk = DB + "." + TempApkProvider.TABLE_TEMP_APK;

            db.execSQL("DELETE FROM " + AppTable.NAME + " WHERE 1");
            db.execSQL("INSERT INTO " + AppTable.NAME + " SELECT * FROM " + tempApp);

            db.execSQL("DELETE FROM " + ApkTable.NAME + " WHERE 1");
            db.execSQL("INSERT INTO " + ApkTable.NAME + " SELECT * FROM " + tempApk);

            db.setTransactionSuccessful();

            getContext().getContentResolver().notifyChange(AppProvider.getContentUri(), null);
            getContext().getContentResolver().notifyChange(ApkProvider.getContentUri(), null);
        } finally {
            db.endTransaction();
            db.execSQL("DETACH DATABASE " + DB); // Can't be done in a transaction.
        }
    }
}
