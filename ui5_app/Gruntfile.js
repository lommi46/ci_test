module.exports = function (grunt) {

    grunt.loadNpmTasks('grunt-exec');

    grunt.initConfig({
        exec: {
          build_ui5: {
            command: 'npm run build'
          }
        }
    });

    grunt.registerTask('default', ['exec:build_ui5']);
};