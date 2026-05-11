function instrument() {
    var availableTags = [
        "institutionCode",
        "collectionCode",
        "catalogNumber",
        "occurrenceID",
        "recordNumber"
    ];

    function split( val ) {
        return val.split( /,\s*/ );
    }

    function extractLast( term ) {
        return split( term ).pop();
    }

    $( "input#termsForUniqueKey:enabled" )
        // don't navigate away from the field on tab when selecting an item
        .bind( "keydown", function( event ) {
            if ( event.keyCode === $.ui.keyCode.TAB &&
                $( this ).data( "autocomplete" ).menu.active ) {
                event.preventDefault();
            }
        })
        .autocomplete({
            minLength: 0,
            source: function( request, response ) {
                // delegate back to autocomplete, but extract the last term
                response( $.ui.autocomplete.filter(
                    availableTags, extractLast( request.term ) ) );
            },
            focus: function() {
                // prevent value inserted on focus
                return false;
            },
            select: function( event, ui ) {
                var terms = split( this.value );
                // remove the current input
                terms.pop();
                // add the selected item
                terms.push( ui.item.value );
                // add placeholder to get the comma-and-space at the end
                terms.push( "" );
                this.value = terms.join( ", " );
                return false;
            }
        });
}

function changeProtocol() {
    var protocol = $('#protocolSelector').val();
    // remove autocomplete binding
    // $('input#termsForUniqueKey:enabled').autocomplete('destroy');
    // $('input#termsForUniqueKey:enabled').unbind('keydown');
    // clear all
    $('div.labile').css('display','none');
    $('div.labile input,textArea').attr('disabled','true');

    // show the selected
    console.log("Displaying protocol : " + protocol);
    $.each(connectionParameters, function(key, obj) {
        $.each(obj, function(j, p) {
            if (p == protocol) {
                $('div#connection_' + key).css('display','block');
                $('div#connection_' + key).removeAttr('style');
                $('div#connection_' + key + ' input,textArea').removeAttr('disabled');
            }
        })
    })

    // re-enable the autocomplete functionality
    instrument();
}

instrument();

$(document).ready(function() {
//$('[name="start_date"]').datepicker({dateFormat: 'yy-mm-dd'});
/* this expands lists of urls into an array of text inputs */
// create a delete element that removes the element before it and itself
var $deleteLink = $('<span class="delete btn btn-sm btn-danger"><i class="fa fa-times"></i> </span>')
    .click(function() {
        $(this).prev().remove();
        $(this).remove();
    });
// handle all urls (including hidden ones)
var urlInputs = $('input[name="url"]');
$('input[name="url"]').addClass('w-100');
$.each(urlInputs, function(i, obj) {
    var urls = $(obj).val().split(',');
    if (urls.length > 1) {
        // more than one url so create an input for each extra one
        $.each(urls,function(i,url) {
            if (i == 0) {
                // existing input gets the first url
                $(obj).val(url);
            }
            else {
                // clone the existing field and inject the next value - adding a delete link
                $(obj).clone()
                    .val(url.trim())
                    .css('width','93%')
                    .addClass('form-control')
                    .insertAfter($(obj).parent().children('input,span').last())
                    .after($deleteLink.clone(true));
            }
        });
    }
});
/* this injects 'add another' functionality to urls */
$.each(urlInputs, function(i, obj) {
    $('<span class="float-end btn btn-outline-dark">Add another</span>')
        .insertAfter($(obj).parent().children('input,span').last())
        .click(function() {
            // clone the original input
            var $clone = $(obj).clone();
            $clone.val('');
            $clone.insertBefore(this);
            $clone.after($deleteLink.clone(true)); // add delete link
        });
});
/* this binds the code to add a new term to the list */
$('#more-terms').click(function() {
    var term = $('#otherKey').val();
    // check that term doesn't already exist
    if ($('#'+term).length > 0) {
        alert(term + " is already present");
    }
    else {
        var newField = "<div class=\"form-group\"><label for='" + term +"'>" + term + "</label>" +
            "<input type='text' class='form-control' id='" + term + "' name='" + term + "'/></div>";
        $('#add-another').parent().append(newField);
    }
});
});
