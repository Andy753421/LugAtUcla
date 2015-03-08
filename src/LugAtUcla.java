package edu.ucla.linux.tutorial;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * Our Main Activity (the user interface). This is launched when the user
 * starts our App. Activity extends Context, so we can access all out
 * application data from within this class using functions such as
 * findViewById.
 */
public class LugAtUcla extends Activity
{
    /* URL Constants - only used if the preferences file is invalid */
    private final String LASTSNAP_JPG = "https://linux.ucla.edu/snapshots/lastsnap.jpg";

    private final String COFFEE_JSON  = "https://linux.ucla.edu/api/coffee.json";
    private final String COFFEE_LOG   = "https://linux.ucla.edu/api/coffee.log";

    /* Settings - this provides access to the actual settings data,
     *            the settings activity is in the Settings.java class
     *            and provides the user interface for editing the settings. */
    private SharedPreferences settings;

    /* Auto refresh */
    private TimerTask task;        // Java auto-refresh task
    private Timer     timer;       // Java auto-refresh timer

    /* Activity Widgets */
    private ImageView lastsnap;    // Last webcam snapshot

    private ImageView potIn;       // Star icon for pos status
    private TextView  potActivity; // Pot activity timestamp field
    private ImageView lidOpen;     // Star icon for lid status
    private TextView  lidActivity; // Lid activity timestamp field

    private TextView  statusView;  // Text view for dumping ASCII status
                                   // This also shows error messages

    /* Convert URL data into a JSON Object
     *   Currently we don't actually need separate functions for reading lines
     *   of text and reading JSON objects However, if we want to support the
     *   COFFEE_LOG URL in the future having a function to get the lines of
     *   text will be useful. */
    private JSONObject readUrlObject(String location)
    {
        List<String> lines = readUrlLines(location);
        String       text  = TextUtils.join("", lines);
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            Log.d("LugAtUcla", "Invalid JSON Object: " + text);
            return null;
        }
    }

    /* Read URL data as lines of text
     *   Android provides several HTTP/URL libraries, like the JSON library, we
     *   arbitrarily picked HttpURLConnection.
     *
     *   Personally, I like to use libraries that are not Android specific
     *   because it makes my code more reusable in other java applications.
     *
     *   We also keep the URL library internal to this function so that we can
     *   easily change it if we ever want to. */
    private List<String> readUrlLines(String location)
    {
        LinkedList<String> lines = new LinkedList<String>();

        try {
            // Open URL
            URL url = new URL(location);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();

            // Open buffered reader
            InputStream       is  = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader    buf = new BufferedReader(isr);

            // Read data into lines array
            String line;
            while ((line = buf.readLine()) != null)
                lines.add(line);

            // Close keep-alive connections
            conn.disconnect();
        }
        catch (MalformedURLException e) {
            Log.d("LugAtUcla", "Invalid URL: " + location);
        }
        catch (IOException e) {
            Log.d("LugAtUcla", "Download failed for: " + location);
        }

        return lines;
    }

    /* Read URL data as an image
     *   This uses the HTTP library directly instead so that we can pass the
     *   InputStream directly to the image loader instead of going through a
     *   data buffer. */
    private Bitmap readUrlImage(String location)
    {
        Bitmap image = null;

        try {
            // Open URL
            URL url = new URL(location);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();

            // Read Image
            InputStream is = conn.getInputStream();
            image = BitmapFactory.decodeStream(is);

            // Close keep-alive connections
            conn.disconnect();
        }
        catch (MalformedURLException e) {
            Log.d("LugAtUcla", "Invalid URL: " + location);
        }
        catch (IOException e) {
            Log.d("LugAtUcla", "Download failed for: " + location);
        }

        return image;
    }

    /* Post data to a URL
     *   Todo - description */
    //final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
    //    public boolean verify(String hostname, SSLSession session) {
    //        return true;
    //    }
    //};
    private List<String> postUrlData(String location, Map<String,String> params)
    {
        // Log in to WiFi
        LinkedList<String> lines = new LinkedList<String>();
        try {
            URL url = new URL(location);
            HttpURLConnection conn  = (HttpURLConnection)url.openConnection();

            // Ignore insecure connections
            //HttpsURLConnection sconn = (HttpsURLConnection)conn;
            //sconn.setHostnameVerifier(DO_NOT_VERIFY);

            // Setup connection
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setReadTimeout(2000);
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("POST");

            // Open buffered writer
            OutputStream       os  = conn.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
            BufferedWriter     bw  = new BufferedWriter(osw);

            // Write URL data
            Log.d("LugAtUcla", "Connect: " + location);
            for (String key : params.keySet()) {
                String val  = params.get(key);
                String ukey = URLEncoder.encode(key, "UTF-8");
                String uval = URLEncoder.encode(val, "UTF-8");
                bw.write(ukey + "=" + uval + "&");
                Log.d("LugAtUcla", "    " + ukey + " = " + uval);
            }

            // Flush output
            bw.flush();
            bw.close();
            os.close();

            // Start connection
            conn.connect();

            // Open buffered reader
            InputStream       is  = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader    br  = new BufferedReader(isr);

            // Read data into lines array
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line);

            // Close keep-alive connections
            conn.disconnect();
        }
        catch (MalformedURLException e) {
            Log.d("LugAtUcla", "Invalid URL: " + location);
        }
        catch (IOException e) {
            Log.d("LugAtUcla", "Connect failed for: " + location);
        }
        return lines;
    }

    /* Display the coffee status to the user */
    private void setStatus(CoffeeStatus status)
    {
        DateFormat fmt = DateFormat.getDateTimeInstance();
        int        yes = android.R.drawable.star_on;
        int        no  = android.R.drawable.star_off;

        // Update graphical display
        //   this provides a nice, readable representation of the coffee
        //   status, but it does not support showing error messages so we only
        //   update it if there have been no errors.
        if (status.error == null) {
            this.potIn.setImageResource(status.potIn ? yes : no);
            this.potActivity.setText(fmt.format(status.potActivity));
            this.lidOpen.setImageResource(status.lidOpen ? yes : no);
            this.lidActivity.setText(fmt.format(status.lidActivity));
        }

        // Set text view contents
        //   normally this shows the same information as the graphical display,
        //   but if there are errors, we will display them here.
        statusView.setTypeface(Typeface.MONOSPACE);
        statusView.setText(status.toString());
    }

    /* Trigger a refresh of the webcam status */
    private void loadWebcamStatus()
    {
        // Lookup URL in the user preferences, LASTSNAP_JPG is the default URL
        String location = this.settings.getString("lastsnap_jpg", this.LASTSNAP_JPG);
        Log.d("LugAtUcla", "loadWebcamStatus - " + location);

        // Update status in a background thread
        //
        // In Android, we normally cannot access the network from the main
        // thread; doing so would cause the user interface to freeze during
        // data transfer.
        //
        // Again, android provides several ways around this, here we use an
        // AsyncTask which lets us run some code in a background thread and
        // then update the user interface once the background code has
        // finished.
        //
        // The first Java Generic parameter are:
        //   1. String - argument for doInBackground, from .execute()
        //   2. Void   - not used here, normally used for progress bars
        //   3. Bitmap - the return type from doInBackground which is
        //               passed to onPostExecute function.
        new AsyncTask<String, Void, Bitmap>() {

            // Called from a background thread, so we don't block the user
            // interface. Using AsyncTask synchronization is handled for us.
            protected Bitmap doInBackground(String... args) {
                // Java passes this as a variable argument array, but we only
                // use the first entry.
                String location = args[0];
                Bitmap image    = LugAtUcla.this.readUrlImage(location);
                return image;
            }

            // Called once in the main thread once doInBackground finishes.
            // This is executed in the Main thread once again so that we can
            // update the user interface.
            protected void onPostExecute(Bitmap image) {
                LugAtUcla.this.lastsnap.setImageBitmap(image);
            }

        }.execute(location);
    }

    /* Trigger a refresh of the coffee pot status */
    private void loadCoffeeStatus()
    {
        // Lookup URL in the user preferences, COFFEE_JSON is the default URL
        String location = this.settings.getString("coffee_json", this.COFFEE_JSON);

        // Notify the user that we're loading
        statusView.setText("Loading..");

        // Log the load
        Log.d("LugAtUcla", "loadCoffeeStatus - " + location);

        // Update status in a background thread
        //
        // See loadWebcamStatus for details
        //
        // We pass a parse the JSON text into a CoffeeStatus in the background
        // as a convenience to localize the JSON objects as much as possible.
        new AsyncTask<String, Void, CoffeeStatus>() {

            // Called from a background thread.
            protected CoffeeStatus doInBackground(String... args) {
                String       location = args[0];
                JSONObject   json     = LugAtUcla.this.readUrlObject(location);
                CoffeeStatus status   = new CoffeeStatus(json);
                return status;
            }

            // Called once in the main thread once doInBackground finishes.
            protected void onPostExecute(CoffeeStatus status) {
                Log.d("LugAtUcla", "CoffeeStatus:\n" + status);
                LugAtUcla.this.setStatus(status);
                Toast.makeText(LugAtUcla.this, "Load complete", Toast.LENGTH_SHORT).show();
            }

        }.execute(location);
    }

    /* Log Into WiFi */
    private void wifiLogin(String ssid)
    {
        String lower = ssid.toLowerCase();
        String upper = ssid.toUpperCase();
        String quote = "\""+upper+"\"";

        // Lookup login info in the user preferences, no default
        final String user = this.settings.getString(lower+"_user", "");
        final String pass = this.settings.getString(lower+"_pass", "");

        // Make sure it's valid
        if ((user == null || user.length() == 0) ||
            (pass == null || pass.length() == 0)) {
                Toast.makeText(this, "No Login Info for "+quote+" WiFi",
                        Toast.LENGTH_SHORT).show();
                return;
            }

        // Format the input
        String location = "https://wireless.cs.ucla.edu/login.html";
        Map<String,String> params = new HashMap<String,String>() {{
            put("buttonClicked", "4");
            put("err_flag",      "0");
            put("err_msg",       "");
            put("info_flag",     "0");
            put("info_msg",      "");
            put("redirect_url",  "http://google.com");
            put("username",      user);
            put("password",      pass);
        }};

        // Tell the user we're starting
        Toast.makeText(this, "Logging into "+quote+" WiFi",
                Toast.LENGTH_SHORT).show();

        // Log in in the background
        new AsyncTask<Object, Void, List<String>>() {

            // Called from a background thread.
            @SuppressWarnings("unchecked")
            protected List<String> doInBackground(Object... args) {
                String             location = (String)args[0];
                Map<String,String> params   = (Map<String,String>)args[1];
                List<String>       lines    = LugAtUcla.this.postUrlData(location, params);
                return lines;
            }

            // Called once in the main thread once doInBackground finishes.
            protected void onPostExecute(List<String> lines) {
                Toast.makeText(LugAtUcla.this, "Load complete", Toast.LENGTH_SHORT).show();
            }

        }.execute(location, params);
    }

    /* Callbacks - there are multiple ways to specify callback but one of the
     *             easiest ones is to add the onClick attribute to
     *             res/layout/main.xml with a function to call when the user
     *             presses the button.
     *
     *             Callback methods assigned in this way must be public and
     *             have the correct argument types. */
    public void onRefresh(View btn)
    {
        this.loadCoffeeStatus();
        this.loadWebcamStatus();
    }
    public void onCsdWifi(View btn)
    {
        this.wifiLogin("csd");
    }

    /* Initialize the App menu
     *   we define the menu in res/menu/menu.xml so all we have to do is load
     *   it using MenuInflater. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /* Handle menu item selections
     *   since we named all our menu entries in the XML file we can just switch
     *   on the menu item ID in order to determine which entry was selected by
     *   the user */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {

            // reload the coffee pot status
            case R.id.refresh:
                this.loadCoffeeStatus();
                this.loadWebcamStatus();
                return true;

            // log into CSD wifi
            case R.id.csd_wifi:
                this.wifiLogin("csd");
                return true;

            // open the user settings list
            //   In Android, activities are started using Intents rather than
            //   open directly. Here we use an "explicit" Intent by specifying
            //   the specific  class (Settings.class) that we want Android to
            //   start for us.
            //
            //   We can also use an "implicit" Intents if we don't know the
            //   class name, such as if we wanted to open a PDF file. In that
            //   case Android would search for an App that can handle PDF files
            //   and deliver the Intent to that App.
            case R.id.settings:
                Intent intent = new Intent(this, Settings.class);
                this.startActivity(intent);
                return true;

            default:
                return false;
        }
    }

    /* Initialize our app
     *   The "lifecycle" of an Android App is rather complex. In our case we
     *   only have to deal with three methods, onCreate, onStart, and onStop.
     *   When the App is first started after boot onCreate and onStart are both
     *   called. When the App is closed onStop is called and the App will no
     *   longer be visible. However, the Activity is kept memory so that only
     *   onStart needs to be called the next time the App is launched.
     *
     *   The Activity is "Create" the first time it is launched after booting
     *   the phone. It should initialize any persistent data such as widgets,
     *   settings, layouts, etc. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Load the user interfaces from the XML UI description
        setContentView(R.layout.main);

        // Set the default preferences values our XML settings file
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        // Grab the settings (containing the server URLs)
        this.settings    = PreferenceManager.getDefaultSharedPreferences(this);

        // Lookup all our user interface widgets
        this.lastsnap    = (ImageView)findViewById(R.id.lastsnap);
        this.potIn       = (ImageView)findViewById(R.id.pot_in);
        this.potActivity =  (TextView)findViewById(R.id.pot_activity);
        this.lidOpen     = (ImageView)findViewById(R.id.lid_open);
        this.lidActivity =  (TextView)findViewById(R.id.lid_activity);
        this.statusView  =  (TextView)findViewById(R.id.status);
    }

    /* The Activity is "Started" it is brought into the foreground, at this point we
     * need to refresh the Coffee and WebCam statuses and start a timer so that
     * the WebCam image will be auto refreshed while the app is visible. */
    @Override
    public void onStart()
    {
        super.onStart();

        // Create the timer function
        //   Once started, the run() method will be called at a fixed rate from
        //   the background. Note that we cannot can't update the UI from the
        //   background. Luckily, we load the status with AsyncTask which
        //   handles updating the display automatically.
        //
        //   We use a standard Java timer here but there are Android specific
        //   timers that could be used.
        this.task  = new TimerTask() {
            public void run() {
                LugAtUcla.this.loadWebcamStatus();
            }
        };

        // Schedule webcam refresh every 5 seconds
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(this.task, 1000*5, 1000*5);

        // Trigger the initial status update
        this.loadCoffeeStatus();
        this.loadWebcamStatus();
    }

    /* The Activity is "Stopped" when it is no longer visible, for example when
     * the "Home" key is pressed or when another activity, such as the Settings
     * Activity, is launched. We need to stop the timer here so that we're not
     * wasting bandwidth when the App is not visible. */
    @Override
    public void onStop()
    {
        super.onStop();

        // Cancel auto-update when the app is not visible
        this.timer.cancel();

        // Clear references to the timer and timer task
        //   This allows their memory to be garbage collected while the App is
        //   in the background. Unfortuantly, we can't seem to re-use the
        //   existing timer so we have to recreate it each time in onCreate.
        this.timer = null;
        this.task  = null;
    }
}
