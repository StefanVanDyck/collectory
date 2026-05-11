<%@ page import="au.org.ala.collectory.ProviderGroupService" %>
<%
  def providerGroupService = grailsApplication.classLoader.loadClass('au.org.ala.collectory.ProviderGroupService').newInstance()
%>
<div class="show-section  card card-body">
  <h2><g:message code="shared.consumers.title01" /></h2>
  <p><g:message code="shared.consumers.des01" args="[providerGroupService.textFormOfEntityType(instance.uid)]" />.
  <br/>
    <g:message code="shared.consumers.des02" />.
  </p>
  <ul class="fancy">
    <g:if test="${instance instanceof au.org.ala.collectory.DataProvider || instance instanceof au.org.ala.collectory.DataResource}">
      <g:each in="${instance.consumerInstitutions + instance.consumerCollections}" var="pg">
        <li><g:link controller="${cl.controllerFromUid(uid:pg.uid)}" action="show" id="${pg.uid}">${pg.name}</g:link> (${pg.uid[0..1] == 'in' ? 'institution' : 'collection'})</li>
      </g:each>
    </g:if>
  </ul>
  <div style="clear:both;"></div>
  <div>
      <span class="buttons long"><g:link class="edit btn btn-outline-dark" action='editConsumers' params="[source:'co']" id="${instance.uid}"><g:message code="shared.consumers.link01" />&nbsp;</g:link></span>
      <span class="buttons long"><g:link class="edit btn btn-outline-dark" action='editConsumers' params="[source:'in']" id="${instance.uid}"><g:message code="shared.consumers.link02" /></g:link></span>
  </div>
</div>
