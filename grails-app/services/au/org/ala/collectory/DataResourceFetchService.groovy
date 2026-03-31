package au.org.ala.collectory

import grails.gorm.transactions.Transactional

class DataResourceFetchService {
    @Transactional
    def findByGuidSafe(String guid) {
        DataResource.findByGuid(guid)
    }
}
