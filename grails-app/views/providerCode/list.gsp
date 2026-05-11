
<%@ page import="au.org.ala.collectory.ProviderCode" %>
<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="${grailsApplication.config.skin.layout}" />
		<g:set var="entityName" value="${message(code: 'providerCode.label', default: 'ProviderCode')}" />
		<title><g:message code="default.list.label" args="[entityName]" /></title>
		<asset:stylesheet src="application.css" />
	</head>
	<body>
		<div class="btn-toolbar">
			<ul class="btn-group">
				<li class="btn btn-outline-dark"><cl:homeLink/></li>
				<li class="btn btn-outline-dark"><span class="fa fa-list"></span><g:link class="list" action="list" params="[max:10000]"> <g:message code="default.list.label" args="[entityName]"/></g:link></li>
				<li class="btn btn-outline-dark"><span class="fa fa-plus"></span><g:link class="create" action="create"> <g:message code="default.new.label" args="[entityName]"/></g:link></li>
			</ul>
		</div>
		<div id="list-providerCode" class="content scaffold-list" role="main">
			<h1><g:message code="default.list.label" args="[entityName]" /></h1>
			<g:if test="${flash.message}">
			<div class="alert alert-info" role="status">${flash.message}</div>
			</g:if>
			<table class="table">
				<thead>
					<tr>
						<g:sortableColumn property="code" title="${message(code: 'providerCode.code.label', default: 'Code')}" />
					</tr>
				</thead>
				<tbody>
				<g:each in="${providerCodeInstanceList}" status="i" var="providerCodeInstance">
					<tr class="${(i % 2) == 0 ? 'even' : 'odd'}">

						<td><g:link action="show" id="${providerCodeInstance.id}">${fieldValue(bean: providerCodeInstance, field: "code")}</g:link></td>

					</tr>
				</g:each>
				</tbody>
			</table>

			<div>
				<tb:paginate controller="providerCode" action="list" total="${providerCodeInstanceTotal}" />
			</div>
		</div>
	</body>
</html>
