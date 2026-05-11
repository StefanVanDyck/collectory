<!-- location -->
<div class="show-section card card-body">
  <h2><g:message code="shared.location.title01" /></h2>
  <div class="row">
    <div class="col-lg-6">
      <table class="table">
        <!-- Address -->
        <tr class="prop">
          <td  class="name category"><g:message code="address.label" default="Address"/></td>

          <td class="value">
            <address>
            ${fieldValue(bean: instance, field: "address.street")}<br/>
            ${fieldValue(bean: instance, field: "address.city")}<br/>
            ${fieldValue(bean: instance, field: "address.state")}
            ${fieldValue(bean: instance, field: "address.postcode")}
            ${fieldValue(bean: instance, field: "address.country")}
            </address>
          </td>
        </tr>

        <!-- Postal -->
        <tr class="prop">
          <td class="name category"><g:message code="providerGroup.address.postal.label" default="Postal"/></td>
          <td class="value">${fieldValue(bean: instance, field: "address.postBox")}</td>
        </tr>

        <!-- Latitude -->
        <tr class="prop">
          <td class="name category"><g:message code="providerGroup.latitude.label" default="Latitude"/></td>
          <td  class="value"><cl:showDecimal value='${instance.latitude}' degree='true'/></td>
        </tr>

        <!-- Longitude -->
        <tr class="prop">
          <td class="name category"><g:message code="providerGroup.longitude.label" default="Longitude"/></td>
          <td class="value"><cl:showDecimal value='${instance.longitude}' degree='true'/></td>
        </tr>

        <!-- State -->
        <tr class="prop">
          <td class="name category"><g:message code="providerGroup.state.label" default="State"/></td>
          <td  class="value">${fieldValue(bean: instance, field: "state")}</td>
        </tr>

        <!-- Email -->
        <tr class="prop">
          <td  class="name category"><g:message code="providerGroup.email.label" default="Email"/></td>
          <td class="value">${fieldValue(bean: instance, field: "email")}</td>
        </tr>

        <!-- Phone -->
        <tr class="prop">
          <td class="name category"><g:message code="providerGroup.phone.label" default="Phone"/></td>
          <td class="value">${fieldValue(bean: instance, field: "phone")}</td>
        </tr>
      </table>
    </div>

    <!-- map spans all rows -->
    <div class="col-lg-6">
      <div id="mapCanvas" style="width:100%;height:400px;"></div>
    </div>
  </div>
  <div style="clear:both;"></div>
  <cl:editButton uid="${instance.uid}" page="/shared/location"/>
</div>

