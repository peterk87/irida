// Default table html DOM layout
export const dom = `
<'row spaced-bottom'<'col-sm-9 toolbar'l><'col-sm-3'f>>
<'row'<'col-sm-12'B>>
<'row'<'col-sm-12'tr>>
<'row'<'col-sm-12'i>>
<'row'<'col-sm-12'p>>
`;

export const domButtonsScroller = `
<'row spaced-bottom'<'col-sm-6'B><'col-sm-6'f>>
<'row'<'col-sm-12'tr>>
<'row'<'col-sm-12'i>>
<'row'<'col-sm-12'p>>
`;

/**
 * Basic format need to display headers in a datatable
 *  title: the text to display in the table headers
 *  data: the key to the columns
 * @param {array} headers list of headers for a table.
 * @return {array} of datatable formatted headers.
 */
export const formatBasicHeaders = headers => {
  return headers.map(title => {
    return {title, data: title};
  });
};

/**
 * All datatables essential need the same default template
 * @param {string} id for the table
 * @return {string} html for the table
 */
export const getDefaultTable = id => {
  return `
<table id='${id}' 
    class='table table-striped' 
    cellspacing='0' width='100%'>
</table>
`;
};