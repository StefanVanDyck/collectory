package au.org.ala.collectory

import au.org.ala.PermissionRequired
import com.opencsv.CSVReader
import grails.converters.JSON
import grails.web.JSONBuilder
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
class AdminController {

    def dataLoaderService, idGeneratorService, metadataService, sitemapService

    def index() {
        redirect(controller: 'manage')
    }

    def home = { }

    def reloadConfig = {
        def resolver = new PathMatchingResourcePatternResolver()
        def configurationResource = resolver.getResource(grailsApplication.config.reloadable.cfgs[0])
        //new ConfigurationResourceListener().onResourceUpdate(configurationResource)
        String res = "<ul>"
        grailsApplication.config.each { key, value ->
            if (value instanceof Map) {
                res += "<p>" + key + "</p>"
                res += "<ul>"
                value.each { k1, v1 ->
                    res += "<li>" + k1 + " = " + v1 + "</li>"
                }
                res += "</ul>"
            }
            else {
                res += "<li>${key} = ${value}</li>"
            }
        }
        render res + "</ul>"
    }

    def showConfig = {
        def target = params.scope ? grailsApplication.config[params.scope] : grailsApplication.config
        if (target instanceof ConfigObject) {
            String res = "<ul>"
            grailsApplication.config.each { key, value ->
                if (value instanceof Map) {
                    res += "<p>" + key + "</p>"
                    res += "<ul>"
                    value.each { k1, v1 ->
                        res += "<li>" + k1 + " = " + v1 + "</li>"
                    }
                    res += "</ul>"
                }
                else {
                    res += "<li>${key} = ${value}</li>"
                }
            }
            render res + "</ul>"
        }
        else {
            render target
        }
    }

    def clearConnectionProfiles = {
        metadataService.clearConnectionProfiles()
        metadataService.clearConnectionParameters()
        render 'Done.'
    }

    def getConnectionProfiles = {
        render metadataService.getConnectionProfiles() as JSON
    }

    def getConnectionProfile = {
        def profile = metadataService.getConnectionProfile(params.profile)
        log.debug profile
        render profile as JSON
    }

    def getConnectionParameters = {
        render metadataService.getConnectionParameters() as JSON
    }

    def search = {
        // use bie search and filter results
        def url = grailsApplication.config.biocacheServicesUrl + "search?q=" + params.term.encodeAsURL() + "&pageSize=1000"

        def conn = new URL(url).openConnection()
        try {
            conn.setConnectTimeout(10000)
            conn.setReadTimeout(50000)
            def json = conn.content.text
            def result = [results: extractSearchResults(json)]
            render(view:'searchResults',model:result)
        } catch (SocketTimeoutException e) {
            log.warn "Timed out searching the BIE. URL= ${url}."
            def error = [error:"Timed out searching the BIE.", totalRecords: 0, decades: null]
            render error as JSON
        } catch (Exception e) {
            log.warn "Failed to search the BIE. ${e.getClass()} ${e.getMessage()} URL= ${url}."
            def error = ["error":"Failed to search the BIE. ${e.getClass()} ${e.getMessage()} URL= ${url}."]
            render error as JSON
        }
    }

    /**
     * Export all tables as JSON
     */
    def export = {
        if (params.table) {
            switch (params.table) {
                case 'contact': render Contact.list() as JSON; break
                case 'contactFor': render ContactFor.list() as JSON; break
                case 'collection': render Collection.list() as JSON; break
                case 'institution': render Institution.list() as JSON; break
                case 'providerCode': render ProviderCode.list() as JSON; break
                case 'providerMap': render ProviderMap.list() as JSON; break
                default:
                    def error = ['error','no such table']
                    render error as JSON
            }
        } else {
            def result = [
                    contact: Contact.list(),
                    contactFor: ContactFor.list(),
                    collection: Collection.list(),
                    institution: Institution.list(),
                    providerCode: ProviderCode.list(),
                    providerMap: ProviderMap.list()
            ]
            render result as JSON
        }
    }

    def importJson = {
        dataLoaderService.importJson()
        // some tests
        if (Institution.findByName("Tasmanian Museum and Art Gallery")?.getContacts()?.size() == 1) {
            render "loaded ok"
        } else {
            render "failed to load correctly"
        }
    }

    def testImport = {
        String resp = ""

        def inst1 = Institution.findByName("Tasmanian Museum and Art Gallery")
        resp += inst1.name + "<br>"
        def fors1 = inst1.getContacts()
        fors1.each {resp += '_' + it.contact + "<br>"}

        def col1 = Collection.findByName("Australian National Herbarium")
        resp += col1.name + "<br>"
        def fors2 = col1.getContacts()
        fors2.each {resp += '_' + it.contact + "<br>"}

        def inst2 = Institution.findByAcronym('CSIRO')
        resp += inst2.name + "<br>"
        def children = inst2?.getCollections()
        children?.each {
            resp += '_' + it.name + "<br>"
        }

        render resp
    }

    def importDataProviders = {
        int beforeCount = DataProvider.count()
        def result = dataLoaderService.importDataProviders("/data/collectory/bootstrap/data_providers.txt")
        int afterCount = DataProvider.count()
        render "Done - ${afterCount-beforeCount} providers created."
        render """
        ${beforeCount} providers before<br/>
        ${result.headerLines} header line found<br/>
        ${result.dataLines} lines of data found<br/>
        ${result.exists} lines of data match existing records<br/>
        ${result.updates} existing records were updated<br/>
        ${result.inserts} records inserted<br/>
        ${result.failures} lines of data could not be processed<br/>
        ${afterCount} providers after"""
    }

    def importDataResources = {
        int beforeCount = DataResource.count()
        def result = dataLoaderService.importDataResources("/data/collectory/bootstrap/data_resources.txt")
        int afterCount = DataResource.count()
        render """
        ${beforeCount} resources before<br/>
        ${result.headerLines} header line found<br/>
        ${result.dataLines} lines of data found<br/>
        ${result.exists} lines of data match existing records<br/>
        ${result.updates} existing records were updated<br/>
        ${result.inserts} records inserted<br/>
        ${result.failures} lines of data could not be processed<br/>
        ${afterCount} resources after"""
    }

    def importBriefDataResources = {
        CSVReader reader = new CSVReader(new FileReader("/data/collectory/bootstrap/infosource.csv"),',' as char)
        String [] nextLine;
        int count = 0

		while ((nextLine = reader.readNext()) != null) {
            def DataResource dr = new DataResource(uid: idGeneratorService.getNextDataResourceId(), resourceType: 'website',
                    name: nextLine[1], websiteUrl: nextLine[2], userLastModified: 'bulk load from BIE')
            if (dr.save(flush:true)) {
                count++
            }
            else {
                log.debug "failed to import ${nextLine}"
                dr.errors.each { log.error it }
            }
        }
        render "${count} data resources created"
    }

    private def extractSearchResults(json) {
        def results = [collections:[], institutions:[], dataResources:[], dataProviders:[], total:0]
        def obj = JSON.parse(json).searchResults
        if (obj?.results) {
            obj.results.each {
                switch (it.idxType) {
                    case "COLLECTION":
                        results.collections << [uid:extractUid(it.guid), name:it.name]
                        break
                    case "INSTITUTION":
                        results.institutions << [uid:extractUid(it.guid), name:it.name]
                        break
                    case "DATASET":
                        results.dataResources << [uid:extractUid(it.guid), name:it.name, provider:it.dataProviderName]
                        break
                    case "DATAPROVIDER":
                        results.dataProviders << [uid:extractUid(it.guid), name:it.name]
                        break
                    default:
                        break
                }
            }
        }
        results.total = results.collections.size() + results.institutions.size() + results.dataProviders.size() + results.dataResources.size()
        return results
    }

    def extractUid = {url ->
        url[url.lastIndexOf('/') + 1..url.size() - 1]
    }

    def speciesGroupLoader = {
        def f = new FileInputStream('/data/collectory/bootstrap/groups.xml').text
        def xml = new XmlSlurper().parseText(f)

        def b = new JSONBuilder()
        def str = ""
        def res = b.build {
            xml.groups.each { grps ->
                str += "+ " + grps.@title + "\n"
                groups(title:grps.@title.text()) {
                    grps.group.each { grp1 ->
                        str +=  " - " + grp1.name() + " - " + grp1.@namekey + "\n"
                        group(namekey:grp1.@namekey.text(), common:grp1.@common.text())
                        grp1.group.each { grp2 ->
                            str +=  " -- " + grp2.name() + grp2.@namekey + "\n"
                            group(namekey:grp2.@namekey.text(), common:grp2.@common.text())
                        }
                    }
                }
            }
        }

        def list = []
        def ch = []
        list << [data:'Fauna', attr: [id:'Fauna', rank:'group'], state:'open', children:ch]
        xml.groups.each { grps ->
            ch << addGroup(grps)
        }

        render list as JSON
    }

    def addGroup(node) {
        def g = [:]
        if (node.name() == 'groups') {
            g.put 'data', node.@title.text()
            g.put 'attr', [id:node.@title.text().replace(' ','_')]
            g.put 'state', 'open'
            g.put 'rank', 'group'
        }
        else {
            g.put 'data', node.@namekey.text() + ' (' + node.@common.text() + ')'
            g.put 'attr', [id:node.@namekey.text(), rank:node.@rank.text() ?: 'group']
        }
        if (node.group.size() > 0) {
            def ch = []
            node.group.each {
                ch << addGroup(it)
            }
            g.put 'children', ch
        }
        return g
    }

    def buildSitemap() {
        sitemapService.build()

        render text: 'done'
    }
}
