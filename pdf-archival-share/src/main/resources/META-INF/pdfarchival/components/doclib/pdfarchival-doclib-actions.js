if(typeof PDFArchive == "undefined" || !PDFArchive)
{
	var PDFArchive = {};
}

PDFArchive.Util = {};

(function()
{
	PDFArchive.Util.HideDependentControls = function(element, htmlIdPrefix)
	{
		// get the field html id
		var fieldHtmlId = element.id;
		// set the value of the hidden field
		var value = YAHOO.util.Dom.get(fieldHtmlId).checked;
		YAHOO.util.Dom.get(fieldHtmlId + "-hidden").value = value;
		// find and hide the dependent controls
		var controls = YAHOO.util.Dom.get(fieldHtmlId + "-tohide").value.split(",");

		for(index in controls)
		{
			var control = new YAHOO.util.Dom.get((htmlIdPrefix + "_" + controls[index]));
			var container = control.parentElement;
			if(value)
			{
				container.style.display = 'none';
			}
			else
			{
				container.style.display = 'block';
			}
		}
	}
})();
