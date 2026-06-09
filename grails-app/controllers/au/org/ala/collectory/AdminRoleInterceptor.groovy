/*
 * Copyright (C) 2023 Atlas of Living Australia
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

import org.apache.http.HttpStatus


class AdminRoleInterceptor {

    CollectoryAuthService collectoryAuthService

    AdminRoleInterceptor(){
        match(controller: 'admin')
        match(controller: 'manage')
        match(controller: 'gbif', actionName:'healthCheck')
        match(controller: 'gbif', actionName:'healthCheckLinked')
        match(controller: 'gbif', actionName:'downloadCSV')
    }

    boolean before() {
        // add an exception for /manage/  and /manage/index/ as it is redirected from  /admin/  - which should be visible to all logged in users.
        if(controllerName == "manage" && (actionName == "index" || actionName == "list")){
            return true
        }

        // check against configured gbifRegistrationRole for admin gbif methods
        if(controllerName == "gbif" && collectoryAuthService?.userInRole(grailsApplication.config.gbifRegistrationRole)){
            return true
        }

        // check roles using userInRoles.userInRole method. also returns true if user has the application configured ROLE_ADMIN.
        if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN)) {
            return true
        }
        response.sendError(HttpStatus.SC_UNAUTHORIZED)
        return false
    }



    boolean after() { true }

    void afterView() {
        // no-op
    }
}
