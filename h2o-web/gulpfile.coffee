fs = require 'fs'
gulp = require 'gulp'
path = require 'path'
through = require 'through2'
clean = require 'gulp-clean'
concat = require 'gulp-concat'
iff = require 'gulp-if'
ignore = require 'gulp-ignore'
order = require 'gulp-order'
header = require 'gulp-header'
footer = require 'gulp-footer'
gutil = require 'gulp-util'
help = require 'gulp-task-listing'
coffee = require 'gulp-coffee'
jade = require 'gulp-jade'
stylus = require 'gulp-stylus'
nib = require 'nib'
yaml = require 'js-yaml'
shorthand = require './src/main/steam/tools/shorthand/shorthand.coffee'

clog = through.obj (file, enc, cb) ->
  console.log file.path
  cb()

expand = (yml) ->
  through.obj (file, enc, cb) ->
    if file.isNull()
      cb null, file
      return
    if file.isStream()
      cb new gutil.PluginError 'gulp-shorthand', 'Streaming not supported'
      return

    symbols = yaml.safeLoad fs.readFileSync yml, encoding: 'utf8'
    implicits = [ 'console', 'Math', 'context', 'lodash' ]
    try
      file.contents = new Buffer shorthand symbols, file.contents.toString(), implicits: implicits
      cb null, file
    catch error
      cb new gutil.PluginError 'gulp-shorthand', error, fileName: file.path

config =
  dir:
    deploy: 'src/main/resources/www/steam/'
  lib:
    js: [
      'lib/stacktrace/stacktrace.js'
      'lib/jquery/dist/jquery.js'
      'lib/lodash/dist/lodash.js'
      'lib/momentjs/min/moment.min.js'
      'lib/typeahead.js/dist/typeahead.jquery.min.js'
      'lib/js-signals/dist/signals.js'
      'lib/crossroads/dist/crossroads.js'
      'lib/hasher/dist/js/hasher.js'
      'lib/bootstrap/dist/js/bootstrap.js'
      'lib/d3/d3.js'
      'lib/knockout/knockout.js'
    ]
    repljs: [
      'lib/jquery/dist/jquery.js'
      'lib/jquery-textrange/jquery-textrange.js'
      'lib/mousetrap/mousetrap.js'
      'lib/mousetrap/plugins/global-bind/mousetrap-global-bind.js'
      'lib/lodash/dist/lodash.js'
      'lib/esprima/esprima.js'
      'lib/coffeescript/extras/coffee-script.js'
      'lib/bootstrap/dist/js/bootstrap.js'
      'lib/d3/d3.js'
      'lib/marked/lib/marked.js'
      'lib/knockout/knockout.js'
    ]
    css: [
      'lib/fontawesome/css/font-awesome.css'
      'lib/bootstrap/dist/css/bootstrap.css'
    ]
    replcss: [
      'lib/fontawesome/css/font-awesome.css'
      'lib/bootstrap/dist/css/bootstrap.css'
    ]
    cssmap: [
      'lib/bootstrap/dist/css/bootstrap.css.map'
    ]
    fonts: [
      'src/main/steam/fonts/*.*'
      'lib/bootstrap/dist/fonts/*.*'
      'lib/fontawesome/fonts/*.*'
    ]
    img: [
      'src/main/steam/images/*.*'
    ]

gulp.task 'build-browser-script', ->
  gulp.src 'src/main/steam/scripts/*.coffee'
    .pipe ignore.exclude /flow.coffee/
    .pipe ignore.exclude /tests.coffee$/
    .pipe iff /global\..+\.coffee$/, (coffee bare: yes), (coffee bare: no)
    .pipe order [ 'global.prelude.js', 'global.*.js', '*.js' ]
    .pipe concat 'steam.js'
    .pipe header '"use strict";(function(){'
    .pipe footer '}).call(this);'
    .pipe gulp.dest config.dir.deploy + 'js/'

gulp.task 'build-repl-script', ->
  gulp.src [ 'src/main/steam/scripts/global.hypergraph.coffee', 'src/main/steam/scripts/flow*.coffee' ]
    .pipe ignore.exclude /tests.coffee$/
    .pipe iff /global\..+\.coffee$/, (coffee bare: yes), (coffee bare: no)
    .pipe order [ 'global.prelude.js', 'global.*.js', '*.js' ]
    .pipe concat 'flow.js'
    .pipe expand 'src/main/steam/tools/shorthand/config.yml'
    .pipe header '"use strict";(function(){ var lodash = window._; window.Steam={};'
    .pipe footer '}).call(this);'
    .pipe gulp.dest config.dir.deploy + 'js/'

gulp.task 'build-test-script', ->
  gulp.src 'src/main/steam/scripts/*.coffee'
    .pipe ignore.exclude /flow.coffee/
    .pipe iff /global\..+\.coffee$/, (coffee bare: yes), (coffee bare: no)
    .pipe order [ 'global.tests.js', 'global.prelude.js', 'global.*.js', '*.js' ]
    .pipe concat 'steam-tests.js'
    .pipe header '"use strict";(function(){'
    .pipe footer '}).call(this);'
    .pipe gulp.dest config.dir.deploy + 'js/'

gulp.task 'build-templates', ->
  gulp.src 'src/main/steam/templates/*.jade'
    .pipe ignore.include /index.jade$/
    .pipe jade pretty: yes
    .pipe gulp.dest config.dir.deploy

gulp.task 'build-repl-templates', ->
  gulp.src 'src/main/steam/templates/*.jade'
    .pipe ignore.include /flow.jade$/
    .pipe jade pretty: yes
    .pipe gulp.dest config.dir.deploy

gulp.task 'build-styles', ->
  gulp.src 'src/main/steam/styles/*.styl'
    .pipe ignore.include /steam.styl$/
    .pipe stylus use: [ nib() ]
    .pipe gulp.dest config.dir.deploy + 'css/'

gulp.task 'build-repl-styles', ->
  gulp.src 'src/main/steam/styles/*.styl'
    .pipe ignore.include /flow.styl$/
    .pipe stylus use: [ nib() ]
    .pipe gulp.dest config.dir.deploy + 'css/'

gulp.task 'compile-browser-assets', ->
  gulp.src config.lib.js
    .pipe concat 'lib.js'
    .pipe gulp.dest config.dir.deploy + 'js/'

  gulp.src config.lib.repljs
    .pipe concat 'lib-flow.js'
    .pipe gulp.dest config.dir.deploy + 'js/'

  gulp.src config.lib.img
    .pipe gulp.dest config.dir.deploy + 'img/'

  gulp.src config.lib.fonts
    .pipe gulp.dest config.dir.deploy + 'fonts/'

  gulp.src config.lib.css
    .pipe concat 'lib.css'
    .pipe gulp.dest config.dir.deploy + 'css/'

  gulp.src config.lib.replcss
    .pipe gulp.dest config.dir.deploy + 'css/'

  gulp.src config.lib.cssmap
    .pipe gulp.dest config.dir.deploy + 'css/'

gulp.task 'watch', ->
  gulp.watch 'src/main/steam/scripts/*.coffee', [ 'build-browser-script', 'build-repl-script' ]
  gulp.watch 'src/main/steam/templates/*.jade', [ 'build-templates', 'build-repl-templates' ]
  gulp.watch 'src/main/steam/styles/*.styl', [ 'build-styles', 'build-repl-styles' ]

gulp.task 'clean', ->
  gulp.src config.dir.deploy, read: no
    .pipe clean()

gulp.task 'test', [ 'build-test-script' ], ->
  require path.resolve config.dir.deploy + 'js/steam-tests.js'

gulp.task 'build', [ 
  'compile-browser-assets'
  'build-browser-script'
  'build-templates'
  'build-styles'
  'build-repl-script'
  'build-repl-templates'
  'build-repl-styles'
]

gulp.task 'default', [ 'build' ]
