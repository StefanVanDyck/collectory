<%@ page import="grails.converters.JSON; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.DataProvider" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="breadcrumbParent"
          content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
    />
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />
    <title><g:message code="upload.gbif.archive" /></title>
    <asset:stylesheet src="application.css" />
</head>
<body>

<h1><g:message code="dataresource.gbifupload.title" /></h1>

<div class="row">

    <div class="col-md-6">
        <div class="mb-3">
            <label for="url"><g:message code="dataresource.gbifupload.label" /></label>
            <g:field type="url" class="form-control" name="url" value=""/>
        </div>
        <div>
            <button class="btn btn-outline-dark" id="downloadArchiveBtn">
                <asset:image class="spinner-progress d-none" uri="/images/spinner.gif"></asset:image>
                <g:message code="dataresource.gbifupload.btn"/>
            </button>
        </div>
        <div id="download-result" class="d-none card card-body" style="margin-top:30px;">
            <g:message code="dataresource.gbifupload.success0"/>:<br/>
            <strong><span id="new-resource-name"></span></strong>.<br/>
            <a href="#" id="new-resource-link"><g:message code="dataresource.gbifupload.success1"/></a>
        </div>
    </div>
    <div class="col-md-6">
        <div class="card card-body">
            <p>
                <g:message code="dataresource.gbifupload.des01" />
                <a href="https://www.gbif.org/"><g:message code="dataresource.gbifupload.link.gbifportal" /></a>.
                <br/>
                <g:message code="dataresource.gbifupload.des02" />.
            </p>
            <p>
                <b><g:message code="dataresource.gbifupload.des03" /></b>: <g:message code="dataresource.gbifupload.des04" />.<br/>
                <g:message code="dataresource.gbifupload.des05" />.
            </p>
            <p>
                <g:message code="dataresource.gbifupload.info0"/> <br/>
                <strong>https://api.gbif.org/v1/occurrence/download/request/0001008-150512124619364.zip</strong>
                <br/>
                <g:message code="dataresource.gbifupload.info1"/> <br/>
            </p>
        </div>
    </div>
</div>

<asset:script>

    var dataResourceUrlBase = '${g.createLink([controller:"dataResource",action:"show"])}/';

    function downloadFile(){

        $('#download-result').addClass("d-none");
        $('.spinner-progress').removeClass("d-none")

        $('#downloadArchiveBtn').prop('disabled', true);
        $.ajax( "downloadGBIFFile?url=" +  $('#url').val() )
        .done(function(data) {
            //alert( "success - " + data.dataResourceUid );
            $('#download-result').removeClass("d-none");
            $('#new-resource-name').html(data.dataResourceName );
            $('#new-resource-link').attr('href', dataResourceUrlBase + data.dataResourceUid );
        })
        .fail(function() {
            alert( "error" );
        })
        .always(function() {
            $('.spinner-progress').addClass("d-none");
            $('#downloadArchiveBtn').prop('disabled', false);
        });
    }

    $('#downloadArchiveBtn').click(downloadFile);

</asset:script>


</body>
</html>
