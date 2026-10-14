/*
 * Copyright (c) 2026 The Finnish Ministry of Education and Culture, The Finnish
 * The Ministry of Economic Affairs and Employment, The Finnish National Agency of
 * Education (Opetushallitus) and The Finnish Development and Administration centre
 * for ELY Centres and TE Offices (KEHA).
 *
 * Licensed under the EUPL-1.2-or-later.
 */

package fi.okm.jod.ohjaaja.cms.sitemap.xml;

import fi.okm.jod.ohjaaja.cms.sitemap.model.SitemapUrl;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Element;

/** Unit tests for the sitemap XML. */
public class SitemapXmlWriterTest {

  @Test
  public void testUrlsetWithLastmodAndAlternates() throws Exception {
    var alternates = new LinkedHashMap<String, String>();
    alternates.put("fi", "https://example.com/ohjaaja/fi/a?x=1&y=2");
    alternates.put("x-default", "https://example.com/ohjaaja/fi/a?x=1&y=2");

    var xml =
        SitemapXmlWriter.write(
            List.of(
                new SitemapUrl(
                    "https://example.com/ohjaaja/fi/a?x=1&y=2",
                    LocalDate.parse("2026-09-30"),
                    alternates),
                new SitemapUrl("https://example.com/ohjaaja/fi", null, alternates)));

    var text = new String(xml, StandardCharsets.UTF_8);
    Assert.assertTrue(text.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    Assert.assertTrue(text.contains("&amp;y=2"));

    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    var urlset = document.getDocumentElement();
    Assert.assertEquals(SitemapXmlWriter.SITEMAP_NAMESPACE, urlset.getNamespaceURI());
    Assert.assertEquals("urlset", urlset.getLocalName());

    var urls = urlset.getElementsByTagNameNS(SitemapXmlWriter.SITEMAP_NAMESPACE, "url");
    Assert.assertEquals(2, urls.getLength());

    var first = (Element) urls.item(0);
    Assert.assertEquals(
        "https://example.com/ohjaaja/fi/a?x=1&y=2",
        first
            .getElementsByTagNameNS(SitemapXmlWriter.SITEMAP_NAMESPACE, "loc")
            .item(0)
            .getTextContent());
    Assert.assertEquals(
        "2026-09-30",
        first
            .getElementsByTagNameNS(SitemapXmlWriter.SITEMAP_NAMESPACE, "lastmod")
            .item(0)
            .getTextContent());
    var links = first.getElementsByTagNameNS(SitemapXmlWriter.XHTML_NAMESPACE, "link");
    Assert.assertEquals(2, links.getLength());
    var link = (Element) links.item(1);
    Assert.assertEquals("alternate", link.getAttribute("rel"));
    Assert.assertEquals("x-default", link.getAttribute("hreflang"));
    Assert.assertEquals("https://example.com/ohjaaja/fi/a?x=1&y=2", link.getAttribute("href"));

    var second = (Element) urls.item(1);
    Assert.assertEquals(
        0,
        second.getElementsByTagNameNS(SitemapXmlWriter.SITEMAP_NAMESPACE, "lastmod").getLength());
  }
}
