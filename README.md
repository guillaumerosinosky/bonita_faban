## Bonita Faban workload sample

### What is it ?

A sample of Faban (http://faban.org) workload for Bonita BPM (https://www.bonitasoft.com/), for test purposes. 

### How to use it ?

First, you need Faban (http://faban.org/download.html) and BonitaBPM (https://www.bonitasoft.com/downloads-v2). 

* Default parameters assume you have Bonita launched on localhost on port 80. You must specify a bar file path (Bonita archive file) in the run parameters (or in `deploy/run.xml`). Caution : *make sure your bar file and your Bonita version are the same*.
* The BPM process should have no form, nor instantiation form (lane/execution tab/instatiation form sub tab), an actor defined (lane/execution tab/actors). The workload will use the first actor defined for this process. 
* For Bonita, It is better for tests purpose to use Docker, as the current scripts need a fresh installation for each run. This simulation have been tested with Faban 1.3 and Bonita 7.4.2.
* Make sure to update build.properties, more precisely the `faban.home` property.
* Default parameters assume you have Bonita launched on localhost on port 80, and Faban on localhost.

### How to launch a run ?

Launch a fresh installation of Bonita :
`docker run -p80:8080 bonita:7.4.2`
Deploy the workload : 
`ant deploy`
You can then schedule a run on http://localhost:9980/ .

This workload is based on CoreHttp (http://faban.org/downloads/corehttp.tar.gz)
