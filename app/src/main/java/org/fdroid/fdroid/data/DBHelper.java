package org.fdroid.fdroid.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Schema.ApkTable;
import org.fdroid.fdroid.data.Schema.AppTable;
import org.fdroid.fdroid.data.Schema.InstalledAppTable;
import org.fdroid.fdroid.data.Schema.RepoTable;

import java.util.ArrayList;
import java.util.List;

class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";

    private static final String DATABASE_NAME = "fdroid";

    private static final String CREATE_TABLE_REPO = "create table "
            + RepoTable.NAME + " (_id integer primary key, "
            + "address text not null, "
            + "name text, description text, inuse integer not null, "
            + "priority integer not null, pubkey text, fingerprint text, "
            + "maxage integer not null default 0, "
            + "version integer not null default 0, "
            + "lastetag text, lastUpdated string,"
            + "isSwap integer boolean default 0,"
            + "username string, password string,"
            + "timestamp integer not null default 0"
            + ");";

    private static final String CREATE_TABLE_APK =
            "CREATE TABLE " + ApkTable.NAME + " ( "
            + "id text not null, "
            + "version text not null, "
            + "repo integer not null, "
            + "hash text not null, "
            + "vercode int not null,"
            + "apkName text not null, "
            + "size int not null, "
            + "sig string, "
            + "srcname string, "
            + "minSdkVersion integer, "
            + "targetSdkVersion integer, "
            + "maxSdkVersion integer, "
            + "permissions string, "
            + "features string, "
            + "nativecode string, "
            + "hashType string, "
            + "added string, "
            + "compatible int not null, "
            + "incompatibleReasons text, "
            + "primary key(id, vercode)"
            + ");";

    private static final String CREATE_TABLE_APP = "CREATE TABLE " + AppTable.NAME
            + " ( "
            + "id text not null, "
            + "name text not null, "
            + "summary text not null, "
            + "icon text, "
            + "description text not null, "
            + "license text not null, "
            + "author text, "
            + "email text, "
            + "webURL text, "
            + "trackerURL text, "
            + "sourceURL text, "
            + "changelogURL text, "
            + "suggestedVercode text,"
            + "upstreamVersion text,"
            + "upstreamVercode integer,"
            + "antiFeatures string,"
            + "donateURL string,"
            + "bitcoinAddr string,"
            + "litecoinAddr string,"
            + "flattrID string,"
            + "requirements string,"
            + "categories string,"
            + "added string,"
            + "lastUpdated string,"
            + "compatible int not null,"
            + "ignoreAllUpdates int not null,"
            + "ignoreThisUpdate int not null,"
            + "iconUrl text, "
            + "iconUrlLarge text, "
            + "primary key(id));";

    private static final String CREATE_TABLE_INSTALLED_APP = "CREATE TABLE " + InstalledAppTable.NAME
            + " ( "
            + InstalledAppTable.Cols.PACKAGE_NAME + " TEXT NOT NULL PRIMARY KEY, "
            + InstalledAppTable.Cols.VERSION_CODE + " INT NOT NULL, "
            + InstalledAppTable.Cols.VERSION_NAME + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.APPLICATION_LABEL + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.SIGNATURE + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.LAST_UPDATE_TIME + " INTEGER NOT NULL DEFAULT 0, "
            + InstalledAppTable.Cols.HASH_TYPE + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.HASH + " TEXT NOT NULL"
            + " );";
    private static final String DROP_TABLE_INSTALLED_APP = "DROP TABLE " + InstalledAppTable.NAME + ";";

    private static final int DB_VERSION = 57;

    private final Context context;

    DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DB_VERSION);
        this.context = context;
    }

    private void populateRepoNames(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 37) {
            return;
        }
        Utils.debugLog(TAG, "Populating repo names from the url");
        final String[] columns = {"address", "_id"};
        Cursor cursor = db.query(RepoTable.NAME, columns,
                "name IS NULL OR name = ''", null, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    String address = cursor.getString(0);
                    long id = cursor.getInt(1);
                    ContentValues values = new ContentValues(1);
                    String name = Repo.addressToName(address);
                    values.put("name", name);
                    final String[] args = {Long.toString(id)};
                    Utils.debugLog(TAG, "Setting repo name to '" + name + "' for repo " + address);
                    db.update(RepoTable.NAME, values, "_id = ?", args);
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
    }

    private void renameRepoId(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 36 || columnExists(db, RepoTable.NAME, "_id")) {
            return;
        }

        Utils.debugLog(TAG, "Renaming " + RepoTable.NAME + ".id to _id");
        db.beginTransaction();

        try {
            // http://stackoverflow.com/questions/805363/how-do-i-rename-a-column-in-a-sqlite-database-table#805508
            String tempTableName = RepoTable.NAME + "__temp__";
            db.execSQL("ALTER TABLE " + RepoTable.NAME + " RENAME TO " + tempTableName + ";");

            // I realise this is available in the CREATE_TABLE_REPO above,
            // however I have a feeling that it will need to be the same as the
            // current structure of the table as of DBVersion 36, or else we may
            // get into strife. For example, if there was a field that
            // got removed, then it will break the "insert select"
            // statement. Therefore, I've put a copy of CREATE_TABLE_REPO
            // here that is the same as it was at DBVersion 36.
            String createTableDdl = "create table " + RepoTable.NAME + " ("
                    + "_id integer not null primary key, "
                    + "address text not null, "
                    + "name text, "
                    + "description text, "
                    + "inuse integer not null, "
                    + "priority integer not null, "
                    + "pubkey text, "
                    + "fingerprint text, "
                    + "maxage integer not null default 0, "
                    + "version integer not null default 0, "
                    + "lastetag text, "
                    + "lastUpdated string);";

            db.execSQL(createTableDdl);

            String nonIdFields = "address, name, description, inuse, priority, " +
                    "pubkey, fingerprint, maxage, version, lastetag, lastUpdated";

            String insertSql = "INSERT INTO " + RepoTable.NAME +
                    "(_id, " + nonIdFields + " ) " +
                    "SELECT id, " + nonIdFields + " FROM " + tempTableName + ";";

            db.execSQL(insertSql);
            db.execSQL("DROP TABLE " + tempTableName + ";");
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error renaming id to _id", e);
        }
        db.endTransaction();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        createAppApk(db);
        db.execSQL(CREATE_TABLE_INSTALLED_APP);
        db.execSQL(CREATE_TABLE_REPO);

        insertRepo(
                db,
                context.getString(R.string.fdroid_repo_name),
                context.getString(R.string.fdroid_repo_address),
                context.getString(R.string.fdroid_repo_description),
                context.getString(R.string.fdroid_repo_pubkey),
                context.getResources().getInteger(R.integer.fdroid_repo_version),
                context.getResources().getInteger(R.integer.fdroid_repo_inuse),
                context.getResources().getInteger(R.integer.fdroid_repo_priority)
        );

        insertRepo(
                db,
                context.getString(R.string.fdroid_archive_name),
                context.getString(R.string.fdroid_archive_address),
                context.getString(R.string.fdroid_archive_description),
                context.getString(R.string.fdroid_archive_pubkey),
                context.getResources().getInteger(R.integer.fdroid_archive_version),
                context.getResources().getInteger(R.integer.fdroid_archive_inuse),
                context.getResources().getInteger(R.integer.fdroid_archive_priority)
        );

        insertRepo(
                db,
                context.getString(R.string.guardianproject_repo_name),
                context.getString(R.string.guardianproject_repo_address),
                context.getString(R.string.guardianproject_repo_description),
                context.getString(R.string.guardianproject_repo_pubkey),
                context.getResources().getInteger(R.integer.guardianproject_repo_version),
                context.getResources().getInteger(R.integer.guardianproject_repo_inuse),
                context.getResources().getInteger(R.integer.guardianproject_repo_priority)
        );

        insertRepo(
                db,
                context.getString(R.string.guardianproject_archive_name),
                context.getString(R.string.guardianproject_archive_address),
                context.getString(R.string.guardianproject_archive_description),
                context.getString(R.string.guardianproject_archive_pubkey),
                context.getResources().getInteger(R.integer.guardianproject_archive_version),
                context.getResources().getInteger(R.integer.guardianproject_archive_inuse),
                context.getResources().getInteger(R.integer.guardianproject_archive_priority)
        );
    }

    private void insertRepo(SQLiteDatabase db, String name, String address,
            String description, String pubKey, int version, int inUse,
            int priority) {

        ContentValues values = new ContentValues();
        values.put(RepoTable.Cols.ADDRESS, address);
        values.put(RepoTable.Cols.NAME, name);
        values.put(RepoTable.Cols.DESCRIPTION, description);
        values.put(RepoTable.Cols.SIGNING_CERT, pubKey);
        values.put(RepoTable.Cols.FINGERPRINT, Utils.calcFingerprint(pubKey));
        values.put(RepoTable.Cols.MAX_AGE, 0);
        values.put(RepoTable.Cols.VERSION, version);
        values.put(RepoTable.Cols.IN_USE, inUse);
        values.put(RepoTable.Cols.PRIORITY, priority);
        values.put(RepoTable.Cols.LAST_ETAG, (String) null);
        values.put(RepoTable.Cols.TIMESTAMP, 0);

        Utils.debugLog(TAG, "Adding repository " + name);
        db.insert(RepoTable.NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        Utils.debugLog(TAG, "Upgrading database from v" + oldVersion + " v" + newVersion);

        migrateRepoTable(db, oldVersion);

        // The other tables are transient and can just be reset. Do this after
        // the repo table changes though, because it also clears the lastetag
        // fields which didn't always exist.
        resetTransient(db, oldVersion);

        addNameAndDescriptionToRepo(db, oldVersion);
        addFingerprintToRepo(db, oldVersion);
        addMaxAgeToRepo(db, oldVersion);
        addVersionToRepo(db, oldVersion);
        addLastUpdatedToRepo(db, oldVersion);
        renameRepoId(db, oldVersion);
        populateRepoNames(db, oldVersion);
        addIsSwapToRepo(db, oldVersion);
        addChangelogToApp(db, oldVersion);
        addIconUrlLargeToApp(db, oldVersion);
        updateIconUrlLarge(db, oldVersion);
        addCredentialsToRepo(db, oldVersion);
        addAuthorToApp(db, oldVersion);
        useMaxValueInMaxSdkVersion(db, oldVersion);
        requireTimestampInRepos(db, oldVersion);
        recreateInstalledAppTable(db, oldVersion);
        addTargetSdkVersionToApk(db, oldVersion);
    }

    /**
     * Migrate repo list to new structure. (No way to change primary
     * key in sqlite - table must be recreated).
     */
    private void migrateRepoTable(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 20) {
            return;
        }
        List<Repo> oldrepos = new ArrayList<>();
        Cursor cursor = db.query(RepoTable.NAME,
                new String[] {"address", "inuse", "pubkey"},
                null, null, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    Repo repo = new Repo();
                    repo.address = cursor.getString(0);
                    repo.inuse = cursor.getInt(1) == 1;
                    repo.signingCertificate = cursor.getString(2);
                    oldrepos.add(repo);
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
        db.execSQL("drop table " + RepoTable.NAME);
        db.execSQL(CREATE_TABLE_REPO);
        for (final Repo repo : oldrepos) {
            ContentValues values = new ContentValues();
            values.put("address", repo.address);
            values.put("inuse", repo.inuse);
            values.put("priority", 10);
            values.put("pubkey", repo.signingCertificate);
            values.put("lastetag", (String) null);
            db.insert(RepoTable.NAME, null, values);
        }
    }

    private void insertNameAndDescription(SQLiteDatabase db,
            int addressResId, int nameResId, int descriptionResId) {
        ContentValues values = new ContentValues();
        values.clear();
        values.put("name", context.getString(nameResId));
        values.put("description", context.getString(descriptionResId));
        db.update(RepoTable.NAME, values, "address = ?", new String[] {
                context.getString(addressResId),
        });
    }

    /**
     * Add a name and description to the repo table, and updates the two
     * default repos with values from strings.xml.
     */
    private void addNameAndDescriptionToRepo(SQLiteDatabase db, int oldVersion) {
        boolean nameExists = columnExists(db, RepoTable.NAME, "name");
        boolean descriptionExists = columnExists(db, RepoTable.NAME, "description");
        if (oldVersion >= 21 || (nameExists && descriptionExists)) {
            return;
        }
        if (!nameExists) {
            db.execSQL("alter table " + RepoTable.NAME + " add column name text");
        }
        if (!descriptionExists) {
            db.execSQL("alter table " + RepoTable.NAME + " add column description text");
        }
        insertNameAndDescription(db, R.string.fdroid_repo_address,
                R.string.fdroid_repo_name, R.string.fdroid_repo_description);
        insertNameAndDescription(db, R.string.fdroid_archive_address,
                R.string.fdroid_archive_name, R.string.fdroid_archive_description);
        insertNameAndDescription(db, R.string.guardianproject_repo_address,
                R.string.guardianproject_repo_name, R.string.guardianproject_repo_description);
        insertNameAndDescription(db, R.string.guardianproject_archive_address,
                R.string.guardianproject_archive_name, R.string.guardianproject_archive_description);

    }

    /**
     * Add a fingerprint field to repos. For any field with a public key,
     * calculate its fingerprint and save it to the database.
     */
    private void addFingerprintToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 44) {
            return;
        }
        if (!columnExists(db, RepoTable.NAME, "fingerprint")) {
            db.execSQL("alter table " + RepoTable.NAME + " add column fingerprint text");
        }
        List<Repo> oldrepos = new ArrayList<>();
        Cursor cursor = db.query(RepoTable.NAME,
                new String[] {"address", "pubkey"},
                null, null, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    Repo repo = new Repo();
                    repo.address = cursor.getString(0);
                    repo.signingCertificate = cursor.getString(1);
                    oldrepos.add(repo);
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
        for (final Repo repo : oldrepos) {
            ContentValues values = new ContentValues();
            values.put("fingerprint", Utils.calcFingerprint(repo.signingCertificate));
            db.update(RepoTable.NAME, values, "address = ?", new String[] {repo.address});
        }
    }

    private void addMaxAgeToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 30 || columnExists(db, RepoTable.NAME, "maxage")) {
            return;
        }
        db.execSQL("alter table " + RepoTable.NAME + " add column maxage integer not null default 0");
    }

    private void addVersionToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 33 || columnExists(db, RepoTable.NAME, "version")) {
            return;
        }
        db.execSQL("alter table " + RepoTable.NAME + " add column version integer not null default 0");
    }

    private void addLastUpdatedToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 35 || columnExists(db, RepoTable.NAME, "lastUpdated")) {
            return;
        }
        Utils.debugLog(TAG, "Adding lastUpdated column to " + RepoTable.NAME);
        db.execSQL("Alter table " + RepoTable.NAME + " add column lastUpdated string");
    }

    private void addIsSwapToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 47 || columnExists(db, RepoTable.NAME, "isSwap")) {
            return;
        }
        Utils.debugLog(TAG, "Adding isSwap field to " + RepoTable.NAME + " table in db.");
        db.execSQL("alter table " + RepoTable.NAME + " add column isSwap boolean default 0;");
    }

    private void addCredentialsToRepo(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 52) {
            return;
        }
        if (!columnExists(db, Schema.RepoTable.NAME, "username")) {
            Utils.debugLog(TAG, "Adding username field to " + RepoTable.NAME + " table in db.");
            db.execSQL("alter table " + RepoTable.NAME + " add column username string;");
        }

        if (!columnExists(db, RepoTable.NAME, "password")) {
            Utils.debugLog(TAG, "Adding password field to " + RepoTable.NAME + " table in db.");
            db.execSQL("alter table " + RepoTable.NAME + " add column password string;");
        }
    }

    private void addChangelogToApp(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 48 || columnExists(db, AppTable.NAME, "changelogURL")) {
            return;
        }
        Utils.debugLog(TAG, "Adding changelogURL column to " + AppTable.NAME);
        db.execSQL("alter table " + AppTable.NAME + " add column changelogURL text");
    }

    private void addIconUrlLargeToApp(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 49 || columnExists(db, AppTable.NAME, "iconUrlLarge")) {
            return;
        }
        Utils.debugLog(TAG, "Adding iconUrlLarge columns to " + AppTable.NAME);
        db.execSQL("alter table " + AppTable.NAME + " add column iconUrlLarge text");
    }

    private void updateIconUrlLarge(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 50) {
            return;
        }
        Utils.debugLog(TAG, "Recalculating app icon URLs so that the newly added large icons will get updated.");
        AppProvider.UpgradeHelper.updateIconUrls(context, db);
        clearRepoEtags(db);
    }

    private void addAuthorToApp(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 53) {
            return;
        }
        if (!columnExists(db, AppTable.NAME, "author")) {
            Utils.debugLog(TAG, "Adding author column to " + AppTable.NAME);
            db.execSQL("alter table " + AppTable.NAME + " add column author text");
        }
        if (!columnExists(db, AppTable.NAME, "email")) {
            Utils.debugLog(TAG, "Adding email column to " + AppTable.NAME);
            db.execSQL("alter table " + AppTable.NAME + " add column email text");
        }
    }

    private void useMaxValueInMaxSdkVersion(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 54) {
            return;
        }
        Utils.debugLog(TAG, "Converting maxSdkVersion value 0 to " + Byte.MAX_VALUE);
        ContentValues values = new ContentValues();
        values.put(ApkTable.Cols.MAX_SDK_VERSION, Byte.MAX_VALUE);
        db.update(ApkTable.NAME, values, ApkTable.Cols.MAX_SDK_VERSION + " < 1", null);
    }

    /**
     * The {@code <repo timestamp="">} value was in the metadata for a long time,
     * but it was not being used in the client until now.
     */
    private void requireTimestampInRepos(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 55) {
            return;
        }
        if (!columnExists(db, RepoTable.NAME, RepoTable.Cols.TIMESTAMP)) {
            Utils.debugLog(TAG, "Adding " + RepoTable.Cols.TIMESTAMP + " column to " + RepoTable.NAME);
            db.execSQL("alter table " + RepoTable.NAME + " add column "
                    + RepoTable.Cols.TIMESTAMP + " integer not null default 0");
        }
    }

    /**
     * By clearing the etags stored in the repo table, it means that next time the user updates
     * their repos (either manually or on a scheduled task), they will update regardless of whether
     * they have changed since last update or not.
     */
    private void clearRepoEtags(SQLiteDatabase db) {
        Utils.debugLog(TAG, "Clearing repo etags, so next update will not be skipped with \"Repos up to date\".");
        db.execSQL("update " + RepoTable.NAME + " set lastetag = NULL");
    }

    private void resetTransient(SQLiteDatabase db, int oldVersion) {
        // Before version 42, only transient info was stored in here. As of some time
        // just before 42 (F-Droid 0.60ish) it now has "ignore this version" info which
        // was is specified by the user. We don't want to weely-neely nuke that data.
        // and the new way to deal with changes to the table structure is to add a
        // if (oldVersion < x && !columnExists(...) and then alter the table as required.
        if (oldVersion >= 42) {
            return;
        }
        context.getSharedPreferences("FDroid", Context.MODE_PRIVATE).edit()
                .putBoolean("triedEmptyUpdate", false).commit();
        db.execSQL("drop table " + AppTable.NAME);
        db.execSQL("drop table " + ApkTable.NAME);
        clearRepoEtags(db);
        createAppApk(db);
    }

    private static void createAppApk(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_APP);
        db.execSQL("create index app_id on " + AppTable.NAME + " (id);");
        db.execSQL(CREATE_TABLE_APK);
        db.execSQL("create index apk_vercode on " + ApkTable.NAME + " (vercode);");
        db.execSQL("create index apk_id on " + ApkTable.NAME + " (id);");
    }

    /**
     * If any column was added or removed, just drop the table, create it again
     * and let the cache be filled from scratch by {@link InstalledAppProviderService}
     * For DB versions older than 43, this will create the {@link InstalledAppProvider}
     * table for the first time.
     */
    private void recreateInstalledAppTable(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 56) {
            return;
        }
        Utils.debugLog(TAG, "(re)creating 'installed app' database table.");
        db.execSQL(DROP_TABLE_INSTALLED_APP);
        db.execSQL(CREATE_TABLE_INSTALLED_APP);
    }

    private void addTargetSdkVersionToApk(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 57) {
            return;
        }
        Utils.debugLog(TAG, "Adding " + ApkTable.Cols.TARGET_SDK_VERSION
                + " columns to " + ApkTable.NAME);
        db.execSQL("alter table " + ApkTable.NAME + " add column "
                + ApkTable.Cols.TARGET_SDK_VERSION + " integer");
    }

    private static boolean columnExists(SQLiteDatabase db,
            String table, String column) {
        return db.rawQuery("select * from " + table + " limit 0,1", null)
                .getColumnIndex(column) != -1;
    }

}
