package org.xbmc.kodi.danmaku.source.local;

import org.xbmc.kodi.danmaku.model.DanmakuItem;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Bilibili XML parser mapping <d p="...">text</d> to DanmakuItem list.
 * Only core attributes are handled: time, mode, size, color.
 */
public class BiliXmlParser {
    public enum ErrorReason {
        MALFORMED,
        IO
    }

    public static final class ParseException extends IOException {
        private final ErrorReason reason;

        public ParseException(String message, ErrorReason reason, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public ErrorReason getReason() {
            return reason;
        }
    }

    public List<DanmakuItem> parse(InputStream inputStream) throws IOException {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, StandardCharsets.UTF_8.name());
            List<DanmakuItem> result = new ArrayList<>();

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                    String pAttr = parser.getAttributeValue(null, "p");
                    String text = parseText(parser);
                    DanmakuItem item = parseItem(pAttr, text);
                    if (item != null) {
                        result.add(item);
                    }
                }
                eventType = parser.next();
            }
            return result;
        } catch (XmlPullParserException e) {
            throw new ParseException("Failed to parse Bili XML", ErrorReason.MALFORMED, e);
        } catch (IOException e) {
            throw new ParseException("IO error while parsing Bili XML", ErrorReason.IO, e);
        }
    }

    private String parseText(XmlPullParser parser) throws IOException, XmlPullParserException {
        int eventType = parser.next();
        if (eventType == XmlPullParser.TEXT) {
            String text = parser.getText();
            // advance to end tag
            parser.nextTag();
            return text == null ? "" : text;
        }
        return "";
    }

    private DanmakuItem parseItem(String pAttr, String text) {
        if (pAttr == null || pAttr.isEmpty()) {
            return null;
        }
        String[] parts = pAttr.split(",");
        if (parts.length < 4) {
            return null;
        }
        try {
            long timeMs = (long) (Float.parseFloat(parts[0]) * 1000);
            int mode = Integer.parseInt(parts[1]);
            float sizeSp = Float.parseFloat(parts[2]);
            int color = (int) Long.parseLong(parts[3]);
            DanmakuItem.Type type = mapMode(mode);
            return new DanmakuItem(timeMs, type, text, color, sizeSp, 1.0f, null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private DanmakuItem.Type mapMode(int mode) {
        switch (mode) {
            case 4:
                return DanmakuItem.Type.BOTTOM;
            case 5:
                return DanmakuItem.Type.TOP;
            case 7:
                return DanmakuItem.Type.POSITIONED;
            case 1:
            case 2:
            case 3:
            default:
                return DanmakuItem.Type.SCROLL;
        }
    }
}
