package au.org.ala.collectory

import grails.gorm.transactions.Transactional

class DataResourceFetchService {
    @Transactional
    def findByGuidSafe(String guid) {
        DataResource.findByGuid(guid)
    }
    @Transactional
    def findByUidSafe(String uid) {
        DataResource.findByUid(uid)
    }
}
