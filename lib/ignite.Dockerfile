# MIT License
# Copyright(c) 2020 Futurewei Cloud
#
#     Permission is hereby granted,
#     free of charge, to any person obtaining a copy of this software and associated documentation files(the "Software"), to deal in the Software without restriction,
#     including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and / or sell copies of the Software, and to permit persons
#     to whom the Software is furnished to do so, subject to the following conditions:
#
#     The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#    
#     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
#     WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

FROM ubuntu

MAINTAINER xzhang2 <xzhang2@futurewei.com>

EXPOSE 10800
EXPOSE 10081
EXPOSE 47100
EXPOSE 47500

RUN apt-get update && apt-get install -y \
    wget openjdk-11-jdk unzip \
    && mkdir /code \
    && cd /code/ \
    && wget https://downloads.apache.org//ignite/2.9.1/apache-ignite-2.9.1-bin.zip \
    &&    unzip -d . apache-ignite-2.9.1-bin.zip \
    && cd apache-ignite-2.9.1-bin/bin \
#    && echo '<?xml version="1.0" encoding="UTF-8"?><beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd"><bean class="org.apache.ignite.configuration.IgniteConfiguration"> <property name="peerClassLoadingEnabled" value="true"/> </bean></beans>' > config.xml

COPY ./target/common-0.1.0-SNAPSHOT.jar /code/apache-ignite-2.9.1-bin/libs/common-0.1.0-SNAPSHOT.jar
COPY ./ncm-ignite.sh /code/apache-ignite-2.9.1-bin/bin/ignite.sh
COPY ./ncm-config.xml  /code/apache-ignite-2.9.1-bin/bin/config.xml

ENTRYPOINT  /code/apache-ignite-2.9.1-bin/bin/ignite.sh /code/apache-ignite-2.9.1-bin/bin/config.xml
