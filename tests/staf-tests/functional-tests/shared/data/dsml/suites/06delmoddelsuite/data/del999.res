HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 962

<?xml version='1.0' encoding='UTF-8' ?>
<soap-env:Envelope 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns:soap-env='http://schemas.xmlsoap.org/soap/envelope/' 
   >
<soap-env:Body>
<batchResponse 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns='urn:oasis:names:tc:DSML:2:0:core' 
   >
   <searchResponse>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
   <searchResponse>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
   <searchResponse>
   <searchResultEntry dn='uid=achassin, ou=People, o=dsmlfe.com'>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
