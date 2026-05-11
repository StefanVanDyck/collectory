<g:form action="save" method="post" >
    <g:hiddenField name="returnTo" value="${returnTo}" />
    <g:hiddenField name="id" value="${providerMapInstance?.id}" />
    <div class="mb-3 ${hasErrors(bean: providerMapInstance, field: 'providerGroup', 'errors')}">
        <label for="institutionSelect"><g:message code="providerMap.institution.label" default="Institution" /></label>
        <g:if test="${!providerMapInstance.dateCreated}">
            <g:select id="institutionSelect" name="institution.id" class="form-select" from="${au.org.ala.collectory.Institution.list([sort: 'name'])}" optionKey="id" value="${providerMapInstance?.institution?.id}" noSelection="${['null':'---- select an institution -----']}"/>
        </g:if>
        <g:else>
            <g:select id="institutionSelect" name="institution.id" class="form-select" from="${au.org.ala.collectory.Institution.list([sort: 'name'])}" optionKey="id" value="${providerMapInstance?.institution?.id}" noSelection="${['null':'---- select an institution -----']}" disabled="disabled" />
        </g:else>
    </div>

    <div class="mb-3 ${hasErrors(bean: providerMapInstance, field: 'providerGroup', 'errors')}">
        <label for="collectionSelect"><cl:required><g:message code="providerMap.collection.label" default="Collection" /></label></cl:required>
        <g:if test="${!providerMapInstance.dateCreated}">
            <g:select id="collectionSelect" name="collection.id" class="form-select" from="${au.org.ala.collectory.Collection.list([sort: 'name'])}" optionKey="id" value="${providerMapInstance?.collection?.id}" noSelection="${['null':'---- select a collection -----']}" />
        </g:if>
        <g:else>
            <g:select id="collectionSelect" name="collection.id" class="form-select" from="${au.org.ala.collectory.Collection.list([sort: 'name'])}" optionKey="id" value="${providerMapInstance?.collection?.id}" noSelection="${['null':'---- select a collection -----']}" disabled="disabled"  />
        </g:else>
    </div>

    <div class="mb-3 ${hasErrors(bean: providerMapInstance, field: 'institutionCodes', 'errors')}">
        <label for="institutionCodes"><g:message code="providerMap.institutionCodes.label" default="Institution codes" /></label>
        <g:select name="institutionCodes" class="form-select" from="${au.org.ala.collectory.ProviderCode.list([sort:'code'])}" multiple="yes" optionKey="id" size="5" value="${providerMapInstance?.institutionCodes*.id}"/>
    </div>

    <div class="mb-3 ${hasErrors(bean: providerMapInstance, field: 'collectionCodes', 'errors')}">
        <label for="collectionCodes"><g:message code="providerMap.collectionCodes.label" default="Collection codes" /></label>
        <g:select name="collectionCodes" class="form-select" from="${au.org.ala.collectory.ProviderCode.list([sort:'code'])}" multiple="yes" optionKey="id" size="5" value="${providerMapInstance?.collectionCodes*.id}" />
    </div>

    <div class="mb-3 ${hasErrors(bean: providerMapInstance, field: 'warning', 'errors')}">
        <label for="warning"><g:message code="providerMap.warning.label" default="Warning" /></label>
        <g:textField name="warning" class="form-control" value="${providerMapInstance?.warning}" />
    </div>

    <div class="mb-3">
        <label for="exact">
            <g:checkBox name="exact" value="${providerMapInstance?.exact}" />
            <g:message code="providerMap.exact.label" default="Exact matches only" />

        </label>
    </div>

    <div class="mb-3">
        <label for="matchAnyCollectionCode">
            <g:checkBox name="matchAnyCollectionCode" value="${providerMapInstance?.matchAnyCollectionCode}" />
            <g:message code="providerMap.matchAnyCollectionCode.label" default="Match Any Collection Code" />
        </label>
    </div>
    <br/>
    <div class="buttons">
        <g:if test="${!providerMapInstance.dateCreated}">
            <span class="button"><g:submitButton name="create" class="save btn btn-success" value="${message(code: 'default.button.create.label', default: 'Create')}" /></span>
        </g:if>
        <g:else>
            <span class="button"><g:actionSubmit class="save btn btn-success" action="update" value="${message(code: 'default.button.update.label', default: 'Update')}" /></span>
            <span class="button"><g:actionSubmit class="delete btn btn-outline-dark" action="delete" value="${message(code: 'default.button.delete.label', default: 'Delete')}" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');" /></span>
        </g:else>

    </div>
</g:form>
<asset:script>

$('#institutionSelect').change(function() {

    console.log("Institution val: " + $('#institutionSelect').val());

    var selectedInst =  $('#institutionSelect').val();

    var collectionListURL = "${grailsApplication.config.grails.serverURL}/ws/institution/" + $('#institutionSelect').val();

    if(selectedInst == 'null'){
        collectionListURL = "${grailsApplication.config.grails.serverURL}/ws/collection/";

        $.get(collectionListURL, function( data ) {
            $('#collectionSelect').empty();
            $('#collectionSelect')
                .append($("<option></option>")
                .attr("value", 'null')
                .text('---- select a collection ----'));
            $.each( data, function( key, collection ) {
                console.log(collection);
                $('#collectionSelect')
                    .append($("<option></option>")
                    .attr("value", collection.id)
                    .text(collection.name));
            });
        });

    } else {

        $.get(collectionListURL, function( data ) {
            $('#collectionSelect').empty();
            $('#collectionSelect')
                .append($("<option></option>")
                .attr("value", 'null')
                .text('---- select a collection ----'));

            $.each( data.collections, function( key, collection ) {
                console.log(collection);
                let collectionUid = collection.uid;
                $.get(collection.uri, function(collectionDetail) {
                    if (collectionDetail.recordsProviderMapping == null) {
                        // only add to the select collections without mappings
                        console.log("Adding " + collectionUid + " because does not have a mapping");
                        $('#collectionSelect')
                            .append($("<option></option>")
                            .attr("value", collection.id)
                            .text(collection.name));
                    } else {
                        console.log("Not adding "+ collectionUid + " because already have a mapping");
                    }
                });
            });
        });
    }
});
</asset:script>
