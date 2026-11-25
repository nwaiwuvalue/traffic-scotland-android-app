package org.me.gcu.nwaiwu_value_s2358062;

import android.location.Location;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Data Model class representing a Traffic Scotland incident or roadwork item parsed from DATEX II XML.
 */
public class Situation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id = "";
    private String feedCategory = "";   // "Unplanned Event" or "Current Roadworks"
    private String situationType = "";  // e.g. "RoadMaintenance", "Accident"
    private String subType = "";
    private String roadName = "";
    private String comment = "";
    private String direction = "";
    private String impact = "";
    private String delaysType = "";
    private String startTime = "";
    private String endTime = "";

    private double fromLat = 0.0;
    private double fromLng = 0.0;
    private double toLat = 0.0;
    private double toLng = 0.0;

    public Situation() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id != null ? id : ""; }

    public String getFeedCategory() { return feedCategory; }
    public void setFeedCategory(String feedCategory) { this.feedCategory = feedCategory != null ? feedCategory : ""; }

    public String getSituationType() { return situationType; }
    public void setSituationType(String situationType) { this.situationType = situationType != null ? situationType : ""; }

    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType != null ? subType : ""; }

    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName != null ? roadName : ""; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment != null ? comment : ""; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction != null ? direction : ""; }

    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact != null ? impact : ""; }

    public String getDelaysType() { return delaysType; }
    public void setDelaysType(String delaysType) { this.delaysType = delaysType != null ? delaysType : ""; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime != null ? startTime : ""; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime != null ? endTime : ""; }

    public double getFromLat() { return fromLat; }
    public void setFromLat(double fromLat) { this.fromLat = fromLat; }

    public double getFromLng() { return fromLng; }
    public void setFromLng(double fromLng) { this.fromLng = fromLng; }

    public double getToLat() { return toLat; }
    public void setToLat(double toLat) { this.toLat = toLat; }

    public double getToLng() { return toLng; }
    public void setToLng(double toLng) { this.toLng = toLng; }

    public double getLatitude() { return fromLat; }
    public double getLongitude() { return fromLng; }

    public boolean hasFromCoordinates() {
        return !Double.isNaN(fromLat) && !Double.isNaN(fromLng) && (fromLat != 0.0 || fromLng != 0.0);
    }

    public boolean hasToCoordinates() {
        return !Double.isNaN(toLat) && !Double.isNaN(toLng) && (toLat != 0.0 || toLng != 0.0);
    }

    public double getPrimaryLat() {
        if (hasFromCoordinates()) return fromLat;
        if (hasToCoordinates()) return toLat;
        return Double.NaN;
    }

    public double getPrimaryLng() {
        if (hasFromCoordinates()) return fromLng;
        if (hasToCoordinates()) return toLng;
        return Double.NaN;
    }

    public boolean hasValidLocation() {
        return hasFromCoordinates() || hasToCoordinates();
    }

    /**
     * Checks if situation has a two-point linear roadwork stretch requiring polyline plotting.
     */
    public boolean hasLinearSegment() {
        return hasValidLocation() && (toLat != 0.0 || toLng != 0.0)
                && (fromLat != toLat || fromLng != toLng);
    }

    /**
     * Calculates distance in kilometres from a reference lat/lng.
     */
    public float distanceKmFrom(double refLat, double refLng) {
        if (!hasValidLocation()) return Float.MAX_VALUE;
        float[] results = new float[1];
        Location.distanceBetween(refLat, refLng, fromLat, fromLng, results);
        return results[0] / 1000f;
    }

    /**
     * Calculates duration in days between startTime and endTime for roadwork duration badges.
     */
    public long getDurationDays() {
        Date start = parseIsoDate(startTime);
        Date end = parseIsoDate(endTime);
        if (start == null || end == null || end.before(start)) return -1;
        long diffMs = end.getTime() - start.getTime();
        return diffMs / (1000L * 60L * 60L * 24L);
    }

    private Date parseIsoDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String cleaned = raw.trim().replaceAll("\\.\\d+", "");
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.UK);
                if (pattern.endsWith("'Z'")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                return sdf.parse(cleaned);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
}