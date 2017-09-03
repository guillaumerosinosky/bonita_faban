# Bonita Faban driver

## What is it ?

A sample of Faban (http://faban.org) workload for Bonita BPM (https://www.bonitasoft.com/). 

## How to use it ?

First, you need Faban (http://faban.org/download.html) and BonitaBPM (). It is better for tests purpose to use Docker, as the current scripts need a fresh installation for each run. 

This simulation have been tested with Faban 1.3 and BonitaBPM 7.4.2.

Default parameters assume you have Bonita launched on localhost on port 80, and Faban on localhost. You can specify a bar file path in the parameters (or in deploy/run.xml)

Make sure to update build.properties, more precisely the faban.home property.

docker run -p80:8080 bonita:7.4.2

ant deploy

You can then schedule a run on http://localhost:9980/ .

Caution : make sure your bar file and your Bonita version are the same.

This workload is based on CoreHttp (http://faban.org/downloads/corehttp.tar.gz)