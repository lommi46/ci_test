module.exports = function (grunt) {

    grunt.loadNpmTasks('grunt-run');

    grunt.initConfig({
        run: {
            options: {
                // ...
            },
            ui5_build: {
                cmd: 'npm',
                args: [
                    'build'
                ]
            }
        }
    });

    grunt.registerTask('default', ['run:ui5_build']);
};