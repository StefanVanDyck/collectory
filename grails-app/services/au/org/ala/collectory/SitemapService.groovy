/*
 * Copyright (C) 2022 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package au.org.ala.collectory

import grails.gorm.transactions.Transactional
import grails.util.Holders
import org.springframework.scheduling.annotation.Scheduled

import java.text.SimpleDateFormat

class SitemapService {

    static lazyInit = !Holders.getConfig().getProperty("sitemap.enabled", Boolean.TYPE, false)

    def grailsApplication


    String URLSET_HEADER = "<?xml version='1.0' encoding='UTF-8'?><urlset xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\" xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
    String URLSET_FOOTER = "</urlset>"

    int MAX_URLS = 50000 // maximum number of URLs in a sitemap file
    int MAX_SIZE = 9*1024*1024 // use 9MB to keep the actual file size below 10MB (a gateway limit)

    File currentFile
    int fileCount = 0
    int countUrls = 0

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY-MM-dd")

    FileWriter fw

    // run daily, initial delay 1hr
    @Scheduled(fixedDelay = 86400000L, initialDelay = 3600000L)
    def build() throws Exception {
        fileCount = 0;

        initWriter()
        buildSitemap()
        closeWriter()

        buildSitemapIndex()
    }

    def buildSitemapIndex() {

        // write parent sitemap file
        fw = new FileWriter(grailsApplication.config.sitemap.dir + "/sitemap.xml")
        fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        for (int i=0;i<fileCount;i++) {

            // move the tmp file
            File newFile = new File(grailsApplication.config.sitemap.dir + "/sitemap" + i + ".xml")
            if (newFile.exists()) {
                newFile.delete()
            }
            new File(grailsApplication.config.sitemap.dir + "/sitemap" + i + ".xml.tmp").renameTo(newFile)

            // add an entry for this new file
            fw.write("<sitemap><loc>" + grailsApplication.config.grails.serverURL + "/sitemap" + i + ".xml" + "</loc>")
            fw.write("<lastmod>" + simpleDateFormat.format(new Date()) + "</lastmod></sitemap>")
        }

        fw.write("</sitemapindex>")
        fw.flush()
        fw.close()
    }

    def initWriter() {
        currentFile = new File(grailsApplication.config.sitemap.dir + "/sitemap" + fileCount + ".xml.tmp")

        fw = new FileWriter(currentFile)

        fw.write(URLSET_HEADER)

        countUrls = 0
        fileCount++
    }

    def closeWriter() {
        fw.write(URLSET_FOOTER)
        fw.flush()
        fw.close()
    }

    def writeUrl(Date lastUpdated, String changefreq, String encodedUrl) {
        if (countUrls >= MAX_URLS || currentFile.size() >= MAX_SIZE) {
            closeWriter()
            initWriter()
        }

        fw.write("<url>")
        fw.write("<loc>" + encodedUrl + "</loc>")
        fw.write("<lastmod>" + simpleDateFormat.format(lastUpdated) + "</lastmod>")
        fw.write("<changefreq>" + changefreq + "</changefreq>")
        fw.write("</url>")

        fw.flush()

        countUrls++
    }

    @Transactional
    def buildSitemap() throws Exception {

        Collection.findAll().each {Collection it ->
            writeUrl(it.lastUpdated, "weekly", grailsApplication.config.grails.serverURL + "/public/show/" + it.uid)
        }

        Institution.findAll().each {Institution it ->
            writeUrl(it.lastUpdated, "weekly", grailsApplication.config.grails.serverURL + "/public/show/" + it.uid)
        }

        DataProvider.findAll().each {DataProvider it ->
            writeUrl(it.lastUpdated, "weekly", grailsApplication.config.grails.serverURL + "/public/show/" + it.uid)
        }

        DataResource.findAllByIsPrivate(false).each {DataResource it ->
            writeUrl(it.lastUpdated, "weekly", grailsApplication.config.grails.serverURL + "/public/show/" + it.uid)
        }
    }
}
