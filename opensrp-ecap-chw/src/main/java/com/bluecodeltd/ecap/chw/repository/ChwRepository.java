package com.bluecodeltd.ecap.chw.repository;

import android.content.Context;

import com.bluecodeltd.ecap.chw.BuildConfig;
import com.bluecodeltd.ecap.chw.application.ChwApplication;
import com.bluecodeltd.ecap.chw.dao.AttendanceDao;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.ecap.chw.dao.ParticipantDao;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.AllConstants;
import org.smartregister.chw.core.repository.CoreChwRepository;

import timber.log.Timber;

public class ChwRepository extends CoreChwRepository {
    private Context context;

    public ChwRepository(Context context, org.smartregister.Context openSRPContext) {
        super(context, AllConstants.DATABASE_NAME, BuildConfig.DATABASE_VERSION, openSRPContext.session(), ChwApplication.getApplicationFlavor().chwAppInstance().getCommonFtsObject(), openSRPContext.sharedRepositoriesArray());
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ChwApplication.registerChimwemweGroupRepository();
        super.onCreate(db);
        HotspotGroupDao.createTable(db);
        ParticipantDao.createTable(db);
        AttendanceDao.createTable(db);
        MonthlyReviewDao.createTable(db);
        com.bluecodeltd.ecap.chw.dao.ChimwemweReferralDao.createTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Timber.w(CoreChwRepository.class.getName(), "Upgrading database from version "
                + oldVersion + " to " + newVersion + ", which will destroy all old data");
        ChwApplication.registerChimwemweGroupRepository();
        ChwRepositoryFlv.onUpgrade(context, db, oldVersion, newVersion);
    }
}
