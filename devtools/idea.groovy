/*
 * No-charge, Yes Attribution (NYA) License 1.0
 *
 * Copyright (c) 2026 Trin Wasinger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, and/or sublicense copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * Any distribution of the Software, substantial portions of the Software, or
 * dependants of the Software must be done free of charge, under this same license
 * unimpeded, and making available free of charge all corresponding source materials.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.sianabeeblebrox.specter.devtools

import io.github.sianabeeblebrox.specter.Specter
import java.lang.management.ManagementFactory
import groovy.xml.MarkupBuilder

import java.nio.file.*

static final def getWritablePath(final String path) {
    final Path p = Paths.get(Specter.rootModPath as String, path)
    Files.createDirectories(p.parent)
    return p
}

static final def xml(final String path) {
    final Path p = getWritablePath(path)
    return Files.notExists(p) ? new MarkupBuilder(new FileWriter(p.toFile())) : null
}

static final def txt(final String path, final Closure f) {
    final Path p = getWritablePath(path)
    if(Files.notExists(p)) {
        f(p)
    }
}

Specter.EVENT_BUS.on('main') { Class<?> clazz, String[] args ->
    xml(".idea/runConfigurations/${clazz.name}.xml")?.component(name: 'ProjectRunConfigurationManager') {
        configuration(default: 'false', name: clazz.name, type: 'Application') {
            option(name: 'JAR_PATH', value: new File(clazz.protectionDomain.codeSource.location.toURI()).path)
            option(name: 'MAIN_CLASS_NAME', value: clazz.name)
            option(name: 'VM_PARAMETERS', value: ManagementFactory.runtimeMXBean.inputArguments.join(' '))
            option(name: 'PROGRAM_PARAMETERS', value: args.toList().join(' '))
            option(name: 'WORKING_DIRECTORY', value: System.getProperty('user.dir'))
            option(name: 'ALTERNATIVE_JRE_PATH_ENABLED', value: 'true')
            option(name: 'ALTERNATIVE_JRE_PATH', value: System.getProperty('java.home'))
            classpathModifications {
                for(final path : System.getProperty('java.class.path').split(';')) {
                    entry(path: path)
                }
            }
            method(v: '2')
        }
    }
}

Specter.EVENT_BUS.on('premain') {
    xml('.idea/modules.xml')?.project(version: '4') {
        component(name: 'ProjectModuleManager') {
            modules {
                module(fileurl: 'file://$PROJECT_DIR$/.idea/Module.iml', filepath: '$PROJECT_DIR$/.idea/Module.iml')
            }
        }
    }

    xml('.idea/Module.iml')?.module(type: 'JAVA_MODULE', version: '4') {
        component(name: 'NewModuleRootManager', 'inherit-compiler-output': 'true') {
            content(url: 'file://$MODULE_DIR$')
            orderEntry(type: 'inheritedJdk')
            orderEntry(type: 'library', name: 'Runtime', level: 'project')
        }
    }

    xml('.idea/libraries/Runtime.xml')?.component(name: 'libraryTable') {
        library(name: 'Runtime') {
            CLASSES {
                for(final URL url : Specter.getClasspathURLs()) {
                    root(url: "jar://${Path.of(url.toURI())}!/")
                }
            }
            SOURCES {

            }
        }
    }

    txt('.gitignore') { Path p ->
        Files.writeString(p, ['.cache', '.idea', new File(this.class.protectionDomain.codeSource.location.toURI()).name].join('\n'))
    }
}


