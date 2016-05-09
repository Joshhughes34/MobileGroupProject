package com.mobilegroupproject.studentorganiser.activities;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.CalendarScopes;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import com.mobilegroupproject.studentorganiser.data.EventsDbHelper;
import android.database.sqlite.SQLiteDatabase;

public class ApiTestActivity extends Activity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String BUTTON_TEXT = "Call Google Calendar API";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };

    private EventsDbHelper eventsDbHelper;
    private SQLiteDatabase eventsDb;

    public List<List<String>> eventsList = new ArrayList<>();
    public String testString;


    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT + "\' button to test the API.");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        eventsDbHelper = new EventsDbHelper(getApplicationContext(),EventsDbHelper.DB_NAME,null,
                com.mobilegroupproject.studentorganiser.data.EventsDbHelper.DB_VERSION);
        eventsDb = eventsDbHelper.getWritableDatabase();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // refresh
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                ApiTestActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Student Organiser")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                putApiDataInEventsDb(getDataFromApi());
                //return convertEventsToString();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Fetch a list of the next 10 events from the primary calendar.
         *
         * @return List of Strings describing returned events.
         * @throws IOException
         */
        private List<List<String>> getDataFromApi() throws IOException {
            // List events from calendars
            DateTime now = new DateTime(System.currentTimeMillis());
            List<List<String>> eventMasterList = new ArrayList<>();
            List<String> calendarIds = getCalendarIds();

            for (int i = 0; i < calendarIds.size(); i++) {

                Events events = mService.events().list(calendarIds.get(i))
                        //.setMaxResults(10)
                        //.setTimeMin(now)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute();
                List<Event> items = events.getItems();

                for (Event event : items) {
                    if (event.getCreator().get("email").toString().contains("@student.lboro.ac.uk")) {

                        DateTime startDateTime = event.getStart().getDateTime();
                        DateTime endDateTime = event.getEnd().getDateTime();

                        if (startDateTime == null){
                            startDateTime = event.getStart().getDate();
                        }

                        List<String> eventDetails = new ArrayList<>();

                        if (event.getSummary() == null) {
                            eventDetails.add("emptyTitle");
                        }
                        else {
                            eventDetails.add(event.getSummary());
                        }

                        eventDetails.add(convertDateTime(startDateTime,endDateTime).get(0));
                        eventDetails.add(convertDateTime(startDateTime,endDateTime).get(1));
                        eventDetails.add(convertDateTime(startDateTime,endDateTime).get(2));

                        if (event.getLocation() == null) {
                            eventDetails.add("emptyBuilding");
                        }
                        else {
                            eventDetails.add(event.getLocation());
                        }

                        if (event.getHangoutLink() == null) {
                            eventDetails.add("emptyHangout");
                        }
                        else {
                            eventDetails.add(event.getHangoutLink());
                        }

                        if (event.getCreator() == null) {
                            eventDetails.add("emptyCreator");
                        }
                        else {
                            eventDetails.add(event.getCreator().toString());
                        }

                        if (event.getColorId() == null) {
                            eventDetails.add("emptyColour");
                        }
                        else {
                            eventDetails.add(event.getColorId());
                        }

                        if (event.getDescription() == null) {
                            eventDetails.add("emptyDescription");
                        }
                        else {
                            eventDetails.add(event.getDescription());
                        }

                        if (event.getICalUID() == null) {
                            eventDetails.add("emptyUid");
                        }
                        else {
                            eventDetails.add(event.getICalUID());
                        }

                        eventMasterList.add(eventDetails);
                    }
                }

            }
            return eventMasterList;
        }

        public List<String> convertDateTime(DateTime startDateTime, DateTime endDateTime) {
            List<String> converted = new ArrayList<>();

            //Get date from startDateTime and add to return variable.
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-mm-dd");
            SimpleDateFormat targetFormat = new SimpleDateFormat("dd-mm-yyyy");

            //Check for all day and get times from startDateTime, then add to return variable.
            if(startDateTime.toString().length() < 11){ // Checking if startDateTime is a time or a date. A date will be larger then 5 characters and a time won't be.
                Log.d("NOT_TIME", startDateTime.toString());
                //converted.add("DATE AS NO TIME GIVEN.");
                try {
                    converted.add(targetFormat.format(apiFormat.parse(startDateTime.toString())));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                converted.add("00:00");
                converted.add("23:59");
            }
            else{
                Log.d("YES_TIME", startDateTime.toString().substring(0, 9));
                try {
                    converted.add(targetFormat.format(apiFormat.parse(startDateTime.toString().substring(0, 9))));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                converted.add(startDateTime.toString().substring(11, 16));
                Log.d("GIMME DAT START TIME", startDateTime.toString().substring(11, 16));
                //converted.add(endDateTime);
                Log.d("GIMME DAT END TIME", endDateTime.toString().substring(11, 16));
                converted.add(endDateTime.toString().substring(11, 16));
            }

            return converted;
        }

        public void putApiDataInEventsDb(List<List<String>> eventMasterList) {
            eventsDbHelper.clearTable("EVENTS_TABLE");

            ContentValues values = new ContentValues();
            for (int i = 0; i < eventMasterList.size(); i++) {
                values.put("TITLE", eventMasterList.get(i).get(0));
                values.put("DATE", eventMasterList.get(i).get(1));
                values.put("STARTTIME", eventMasterList.get(i).get(2));
                values.put("ENDTIME", eventMasterList.get(i).get(3));
                values.put("BUILDING", eventMasterList.get(i).get(4));
                values.put("HANGOUT_LINK", eventMasterList.get(i).get(5));
                values.put("CREATOR", eventMasterList.get(i).get(6));
                values.put("COLOUR_ID", eventMasterList.get(i).get(7));
                values.put("DESCRIPTION", eventMasterList.get(i).get(8));
                values.put("UID", eventMasterList.get(i).get(9));

                eventsDb.insert("EVENTS_TABLE", null, values);
            }
        }

//        private List<String> convertEventsToString() throws IOException {   // Test for calendar converter
//            List<List<String>> s = getDataFromApi();
//
//            List<String> list = new ArrayList<>();
//
//                    for (int i=0; i < s.size(); i++) {
//                        list.add(s.get(i).get(0) + " - " + s.get(i).get(1) + " ::::: " + s.get(i).get(6).toString().toLowerCase());
//                    }
//
//            return list;
//
//        }

        private List<String> getCalendarIds() throws IOException {
            String pageToken = null;
            List<String> calendarIds = new ArrayList<>();

            do {
                CalendarList calendarList = mService.calendarList().list().setPageToken(pageToken).execute();
                List<CalendarListEntry> items = calendarList.getItems();

                for (CalendarListEntry calendarListEntry : items) {
                    calendarIds.add(calendarListEntry.getId());
                }
                pageToken = calendarList.getNextPageToken();
            } while (pageToken != null);
            return calendarIds;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        public void populateEventsList() {

            String[] columns = {
                    "TITLE", "DATE", "STARTTIME", "ENDTIME", "BUILDING", "HANGOUT_LINK", "CREATOR",
                    "COLOUR_ID", "DESCRIPTION", "UID"
            };

            Cursor c = eventsDb.query(
                    "EVENTS_TABLE",
                    columns,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            if (c.moveToFirst()) {
                //testString = c.getString(c.getColumnIndex("UID"));
                //Log.d("hello",testString);
                do {
                    Log.d("hello TITLE",c.getString(c.getColumnIndex("TITLE")));
                    Log.d("hello DATE",c.getString(c.getColumnIndex("DATE")));
                    Log.d("hello STARTTIME",c.getString(c.getColumnIndex("STARTTIME")));
                    Log.d("hello ENDTIME", c.getString(c.getColumnIndex("ENDTIME")));
                    Log.d("hello BUILDING",c.getString(c.getColumnIndex("BUILDING")));
                    Log.d("hello HANGOUT_LINK",c.getString(c.getColumnIndex("HANGOUT_LINK")));
                    Log.d("hello CREATOR",c.getString(c.getColumnIndex("CREATOR")));
                    Log.d("hello COLOUR_ID",c.getString(c.getColumnIndex("COLOUR_ID")));
                    Log.d("hello DESCRIPTION",c.getString(c.getColumnIndex("DESCRIPTION")));
                    Log.d("hello UID",c.getString(c.getColumnIndex("UID")));

                    List<String> eventData = new ArrayList<>();
                    eventData.add(c.getString(c.getColumnIndex("TITLE")));
                    eventData.add(c.getString(c.getColumnIndex("DATE")));
                    eventData.add(c.getString(c.getColumnIndex("STARTTIME")));
                    eventData.add(c.getString(c.getColumnIndex("ENDTIME")));
                    eventData.add(c.getString(c.getColumnIndex("BUILDING")));
                    eventData.add(c.getString(c.getColumnIndex("HANGOUT_LINK")));
                    eventData.add(c.getString(c.getColumnIndex("CREATOR")));
                    eventData.add(c.getString(c.getColumnIndex("COLOUR_ID")));
                    eventData.add(c.getString(c.getColumnIndex("DESCRIPTION")));
                    eventData.add(c.getString(c.getColumnIndex("UID")));
                    eventsList.add(eventData);
                } while (c.moveToNext());
            } else {
                List<String> errorData = new ArrayList<>();
                errorData.add("error TITLE");
                errorData.add("error DATE");
                errorData.add("error STARTTIME");
                errorData.add("error ENDTIME");
                errorData.add("error BUILDING");
                errorData.add("error HANGOUT_LINK");
                errorData.add("error CREATOR");
                errorData.add("error COLOUR_ID");
                errorData.add("error DESCRIPTION");
                errorData.add("error UID");
            }
        }

        @Override
        protected void onPostExecute(Void output) {

            populateEventsList();

            mProgress.hide();

            for(int i=0; i < eventsList.size(); i++){
                mOutputText.append(TextUtils.join("\n", eventsList.get(i)));
                //mOutputText.append(eventsList.get(i).get(1));
                mOutputText.append("\n");
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            ApiTestActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }
}