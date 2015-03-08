package edu.ucla.linux.tutorial;

import android.util.Log;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/* Android provides multiple JSON libraries,
 * we arbitrarily chose to use the org.json library */
import org.json.JSONException;
import org.json.JSONObject;

/*
 * CoffeeStatus represents the information retrieved from the Coffee API.
 *
 * We could use the JSON Object directly if we wanted to, but encapsulating it
 * in an object is useful because we can deal with all different error cases in
 * the same place and not have to worry about that in the rest of the app. It
 * also lets decouples the user interface code from the JSON interface. 
 */
public class CoffeeStatus
{
    // If we get a parser error we set it in this string so that the user knows
    // about the error.
    public String   error       = null;

    public boolean  potIn       = false;      // Is the coffee pot currently in the Coffee maker?
    public Date     potActivity = new Date(); // Last time the coffee pot was moved
    public boolean  lidOpen     = false;      // Is the coffee pot lid currently open?
    public Date     lidActivity = new Date(); // Last time the lid was opened or closed

    public CoffeeStatus(JSONObject json) {
        try {
            // Note: does not handle timezone correctly
            DateFormat fmt = new SimpleDateFormat("yyyy-MM-DD'T'hh:mm:ss");

            // Lookup objects
            JSONObject pot = json.getJSONObject("pot");
            JSONObject lid = json.getJSONObject("lid");

            // Set properties
            this.potIn       = pot.getInt("currently_in") != 0;
            this.potActivity = fmt.parse(pot.getString("last_activity"));
            this.lidOpen     = lid.getInt("currently_open") != 0;
            this.lidActivity = fmt.parse(lid.getString("last_activity"));
        }
        catch (NullPointerException e) {
            // Kind of a hack, avoids extra checking in LugAtUcla
            this.error = "Error downloading coffee status";
        }
        catch (JSONException e) {
            this.error = "Error parsing coffee status: " + e.toString();
        }
        catch (ParseException e) {
            this.error = "Error parsing dates" + e.toString();
        }
    }

    // Convenience function to format coffee pot status as a string.
    public String toString() {
        // If there was an error just output error status,
        // the rest of the object is probably invalid.
        if (this.error != null)
            return this.error;

        // Get the users preferred time format.
        DateFormat fmt = DateFormat.getDateTimeInstance();

        String potStat = this.potIn   ? "In"   : "Out";
        String lidStat = this.lidOpen ? "Open" : "Closed";
        String potTime = fmt.format(this.potActivity);
        String lidTime = fmt.format(this.lidActivity);

        return
            String.format("Pot %-6s at %s\n", potStat, potTime) +
            String.format("Lid %-6s at %s\n", lidStat, lidTime);
    }
}
