package org.me.gcu.nwaiwu_value_s2358062;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;

/**
 * Utility class to parse DATEX II XML data from Traffic Scotland REST APIs using XmlPullParser.
 */
public class DatexPullParser {

    private static final String TAG = "DatexPullParser";

    /**
     * Parses DATEX II XML string into a list of Situation model objects.
     *
     * @param xmlData      Raw XML string content downloaded from Traffic Scotland REST feed.
     * @param feedCategory Category identifier ("Unplanned Event" or "Current Roadworks").
     * @return List of parsed Situation items.
     */
    public static ArrayList<Situation> parse(String xmlData, String feedCategory) {
        ArrayList<Situation> situations = new ArrayList<>();

        if (xmlData == null || xmlData.trim().isEmpty()) {
            return situations;
        }

        // Strip leading garbage bytes before opening tag if present
        int startIdx = xmlData.indexOf("<");
        if (startIdx > 0) {
            xmlData = xmlData.substring(startIdx);
        }

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(new StringReader(xmlData));

            Situation current = null;

            // Flags to track XML tag context
            boolean inComment = false;
            boolean inTo = false;
            boolean inFrom = false;
            boolean inDescriptor = false;

            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = xpp.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if ("situation".equalsIgnoreCase(tagName)) {
                        current = new Situation();
                        current.setFeedCategory(feedCategory);
                        String idAttr = xpp.getAttributeValue(null, "id");
                        if (idAttr != null) current.setId(idAttr);

                    } else if (current != null) {
                        if ("situationRecord".equalsIgnoreCase(tagName)) {
                            String typeAttr = xpp.getAttributeValue(
                                    "http://www.w3.org/2001/XMLSchema-instance", "type");
                            if (typeAttr == null && xpp.getAttributeCount() > 0) {
                                typeAttr = xpp.getAttributeValue(0);
                            }
                            if (typeAttr != null) current.setSituationType(typeAttr);

                        } else if ("overallStartTime".equalsIgnoreCase(tagName)) {
                            current.setStartTime(xpp.nextText().trim());

                        } else if ("overallEndTime".equalsIgnoreCase(tagName)) {
                            current.setEndTime(xpp.nextText().trim());

                        } else if ("delaysType".equalsIgnoreCase(tagName)) {
                            current.setDelaysType(xpp.nextText().trim());

                        } else if ("generalPublicComment".equalsIgnoreCase(tagName)) {
                            inComment = true;

                        } else if ("tpegDirection".equalsIgnoreCase(tagName)) {
                            current.setDirection(xpp.nextText().trim());

                        } else if ("to".equalsIgnoreCase(tagName)) {
                            inTo = true;

                        } else if ("from".equalsIgnoreCase(tagName)) {
                            inFrom = true;

                        } else if ("descriptor".equalsIgnoreCase(tagName)) {
                            inDescriptor = true;

                        } else if ("latitude".equalsIgnoreCase(tagName)) {
                            double lat = parseDoubleSafe(xpp.nextText().trim());
                            if (inTo) {
                                current.setToLat(lat);
                            } else {
                                current.setFromLat(lat);
                            }

                        } else if ("longitude".equalsIgnoreCase(tagName)) {
                            double lng = parseDoubleSafe(xpp.nextText().trim());
                            if (inTo) {
                                current.setToLng(lng);
                            } else {
                                current.setFromLng(lng);
                            }

                        } else if ("value".equalsIgnoreCase(tagName)) {
                            String text = xpp.nextText().trim();
                            if (inComment && current.getComment().isEmpty()) {
                                current.setComment(text);
                            } else if (inDescriptor && (inTo || inFrom) && current.getRoadName().isEmpty()) {
                                current.setRoadName(text);
                            }

                        } else if ("roadOrCarriagewayOrLaneManagementType".equalsIgnoreCase(tagName)
                                || "roadMaintenanceType".equalsIgnoreCase(tagName)) {
                            current.setSubType(xpp.nextText().trim());
                        }
                    }

                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("situation".equalsIgnoreCase(tagName) && current != null) {
                        situations.add(current);
                        current = null;
                    } else if ("generalPublicComment".equalsIgnoreCase(tagName)) {
                        inComment = false;
                    } else if ("to".equalsIgnoreCase(tagName)) {
                        inTo = false;
                    } else if ("from".equalsIgnoreCase(tagName)) {
                        inFrom = false;
                    } else if ("descriptor".equalsIgnoreCase(tagName)) {
                        inDescriptor = false;
                    }
                }

                eventType = xpp.next();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing DATEX II XML: " + e.getMessage(), e);
        }

        return situations;
    }

    /**
     * Safely converts string coordinates into double precision values.
     */
    private static double parseDoubleSafe(String val) {
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}