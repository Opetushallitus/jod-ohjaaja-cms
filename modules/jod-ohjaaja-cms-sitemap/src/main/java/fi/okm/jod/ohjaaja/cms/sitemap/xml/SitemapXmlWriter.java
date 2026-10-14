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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Writes a sitemaps.org 0.9 urlset with hreflang alternates in the xhtml namespace. */
public final class SitemapXmlWriter {

  static final String SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";
  static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

  private SitemapXmlWriter() {}

  public static byte[] write(List<SitemapUrl> urls) {
    var outputStream = new ByteArrayOutputStream();
    try {
      // The JDK implementation, not whichever StAX provider happens to be visible in OSGi
      var writer =
          XMLOutputFactory.newDefaultFactory()
              .createXMLStreamWriter(outputStream, StandardCharsets.UTF_8.name());
      writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
      writer.writeCharacters("\n");
      writer.setDefaultNamespace(SITEMAP_NAMESPACE);
      writer.setPrefix("xhtml", XHTML_NAMESPACE);
      writer.writeStartElement(SITEMAP_NAMESPACE, "urlset");
      writer.writeDefaultNamespace(SITEMAP_NAMESPACE);
      writer.writeNamespace("xhtml", XHTML_NAMESPACE);
      writer.writeCharacters("\n");
      for (var url : urls) {
        writeUrl(writer, url);
      }
      writer.writeEndElement();
      writer.writeCharacters("\n");
      writer.writeEndDocument();
      writer.close();
    } catch (XMLStreamException e) {
      throw new IllegalStateException("Failed to write sitemap", e);
    }
    return outputStream.toByteArray();
  }

  private static void writeUrl(XMLStreamWriter writer, SitemapUrl url) throws XMLStreamException {
    writer.writeStartElement(SITEMAP_NAMESPACE, "url");
    writeElement(writer, "loc", url.loc());
    if (url.lastmod() != null) {
      writeElement(writer, "lastmod", url.lastmod().format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
    for (var alternate : url.alternates().entrySet()) {
      writer.writeEmptyElement(XHTML_NAMESPACE, "link");
      writer.writeAttribute("rel", "alternate");
      writer.writeAttribute("hreflang", alternate.getKey());
      writer.writeAttribute("href", alternate.getValue());
    }
    writer.writeEndElement();
    writer.writeCharacters("\n");
  }

  private static void writeElement(XMLStreamWriter writer, String name, String value)
      throws XMLStreamException {
    writer.writeStartElement(SITEMAP_NAMESPACE, name);
    writer.writeCharacters(value);
    writer.writeEndElement();
  }
}
