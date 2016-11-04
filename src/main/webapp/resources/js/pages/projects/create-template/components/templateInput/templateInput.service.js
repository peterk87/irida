const $ = require('jquery');
const angular = require('angular');

export class TemplateInputService {
  constructor($http, $window) {
    this.$http = $http;
    this.url = $window.location.href;
    this.$window = $window;
  }
  getFieldsForTemplates(template = '') {
    return this.$http.get(`${this.url}/current?template=${template}`)
      .then(response => response.data);
  }
  saveTemplate(template, redirectUrl) {
    // Need to use jquery since I made a bad decision early on to wrap $http posts
    // to be form data :(
    return $.ajax({
      type: 'POST',
      contentType: 'application/json; charset=utf-8',
      url: `${this.url}/save-template/${template.name}`,
      data: angular.toJson(template.fields)
    }).done(response => {
      console.log(response);
      // this.$window.location.href = `${redirectUrl}?template=${template.name}`;
    }).fail(() => {
      console.log('Boo');
    });
  }
}
