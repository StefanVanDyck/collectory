<html xmlns="http://www.w3.org/1999/xhtml" dir="ltr" lang="en-US">
  <head>
	  <title>There was a problem...</title>
      <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
      <g:javascript library="collectory" />
	  <style type="text/css">
	  		.error-message {
	  			border: 1px solid #b2d1ff;
	  			padding: 5px;
	  			background-color:#f3f8fc;
                color: #006dba;
	  		}
	  		.stack {
	  			border: 1px solid black;
	  			padding: 5px;
	  			overflow:auto;
	  			height: 300px;
	  		}
            h3 {
                margin-top: 25px;
                margin-bottom: 25px;
            }
            .action {
                font-weight: bold;
            }
	  </style>
  </head>

  <body>

    <div class="container">
     <g:if test="${flash.errorMessage}">
		 <h3>Oh snap!</h3>
		 <p><strong>${flash.errorMessage}</strong></p>
          <hr>
		  <p>If this is the first time this page has appeared, <span class="action">try the refresh button in your browser.</span></p>
		  <p>If this fails, <span class="action">try to return to the <a href="/collectory">home page</a> and start again.</span></p>
		  <p>If this page is still displayed, <span class="action">please report the incident to ${grailsApplication.config.skin.orgNameShort} support.
		  <p>Thanks for your patience.</p>
	 </g:if>
  </body>
</html>