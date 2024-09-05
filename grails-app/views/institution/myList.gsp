<%@ page import="au.org.ala.collectory.ProviderGroup" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="breadcrumbParent"
              content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
        />
        <meta name="layout" content="${grailsApplication.config.skin.layout}" />
        <g:set var="entityName" value="${message(code: 'institution.label', default: 'Institution')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
        <div class="nav">
            <ul>
            <li><span class="menuButton"><cl:homeLink/></span></li>
            <li><span class="menuButton"><g:link class="list" action="list"><g:message code="default.list.label" args="[entityName]" /></g:link></span></li>
            <li><span class="menuButton"><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></span></li>
            </ul>
        </div>
        <div class="body">
            <h1><g:message code="institution.myList.label" default="My institutions" /></h1>
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>
            <h1><g:message code="institutions.myList.label" default="My institutions" /></h1>
            <g:render template="/shared/institutionList" model="[institutionInstanceList: institutions]"/>
        </div>
    </body>
</html>
