<g:if test="${instance.resourceType == 'species-list'}">
    <section class="public-metadata">
        <g:if test="${showHeaders}">
            <h4><g:message code="public.sdr.content.label12" /></h4>
        </g:if>
        <div class="webSite">
            <a class='external_icon' target="_blank"
               href="${grailsApplication.config.speciesListToolUrl}${instance.uid}"><g:message code="public.sdr.content.link03" /></a>
        </div>
    </section>
</g:if>
<g:elseif test="${instance.resourceType == 'publications'}">
    <section class="public-metadata">
        <g:if test="${showHeaders}">
            <h4><g:message code="public.website" /></h4>
        </g:if>
        <div class="webSite">
            <a class='external_icon' target="_blank"
               href="${instance.websiteUrl}"><g:message code="public.sdr.content.link05" /></a>
        </div>
    </section>
</g:elseif>
<g:elseif test="${instance.websiteUrl}">
    <section class="public-metadata">
        <g:if test="${showHeaders}">
            <h4><g:message code="public.website" /></h4>
        </g:if>
        <div class="webSite">
            <a class='external_icon' target="_blank"
               href="${instance.websiteUrl}"><g:message code="public.sdr.content.link04" /></a>
        </div>
    </section>
</g:elseif>