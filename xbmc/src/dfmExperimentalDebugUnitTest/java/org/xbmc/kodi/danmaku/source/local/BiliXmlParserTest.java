package org.xbmc.kodi.danmaku.source.local;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xbmc.kodi.danmaku.model.DanmakuItem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class BiliXmlParserTest {

    @Test
    public void parsesBasicModes() throws Exception {
        String xml = "<i>" +
                "<d p=\"0.5,1,25,16777215,0,0,0,0\">hello</d>" +
                "<d p=\"2.0,5,30,255,0,0,0,0\">top</d>" +
                "<d p=\"3.0,4,20,65280,0,0,0,0\">bottom</d>" +
                "</i>";

        BiliXmlParser parser = new BiliXmlParser();
        List<DanmakuItem> items = parser.parse(stream(xml));

        assertEquals(3, items.size());
        DanmakuItem first = items.get(0);
        assertEquals(500L, first.getTimeMs());
        assertEquals(DanmakuItem.Type.SCROLL, first.getType());
        assertEquals("hello", first.getText());
        assertEquals(25f, first.getTextSizeSp(), 0.001f);
        assertEquals(16777215, first.getColor());

        DanmakuItem top = items.get(1);
        assertEquals(DanmakuItem.Type.TOP, top.getType());

        DanmakuItem bottom = items.get(2);
        assertEquals(DanmakuItem.Type.BOTTOM, bottom.getType());
    }

    @Test
    public void skipsInvalidEntries() throws Exception {
        String xml = "<i>" +
                "<d p=\"not-a-number,1,25,0\">broken</d>" +
                "<d>missingp</d>" +
                "<d p=\"1.0,1,20,255\">ok</d>" +
                "</i>";

        BiliXmlParser parser = new BiliXmlParser();
        List<DanmakuItem> items = parser.parse(stream(xml));

        assertEquals(1, items.size());
        assertEquals("ok", items.get(0).getText());
    }

    private ByteArrayInputStream stream(String xml) throws IOException {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
