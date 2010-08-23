grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'

grails.project.dependency.resolution = {

	inherits 'global'

	log 'warn'

	repositories {
		grailsPlugins()
		grailsHome()
		grailsCentral()

		mavenRepo 'http://download.java.net/maven/2/'
		mavenCentral()
	}

	dependencies {
		runtime('com.enigmastation:ci-bayes:1.0.8') {
			transitive = false
//			excludes 'javax.transaction',
//			         'org.hibernate',
//			         'hsqldb',
//			         'org.testng',
//			         'org.springframework',
//			         'org.aspectj',
//			         'org.apache.ant',
//			         'rome',
//			         'nekohtml',
//			         'javatar'
		}
		runtime('org.javolution:javolution:5.2.6') {
			transitive = false
		}
	}
}
