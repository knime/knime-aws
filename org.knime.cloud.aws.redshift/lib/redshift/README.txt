License page https://github.com/aws/amazon-redshift-jdbc-driver/blob/master/LICENSE

The libraries in the jdbc folder are automatically downloaded from Maven 
(e.g. https://mvnrepository.com/artifact/com.amazon.redshift/redshift-jdbc42/2.1.0.3) using the dependencies specified in the
../fetch_jars/pom.xml files. 
We need to use several fetch jar projects to download different versions of the same driver.