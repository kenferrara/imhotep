---
layout: default
title: Data File Requirements
permalink: /docs/data-file-requirements/
---

Each data file you create to upload to your dataset must follow requirements for field and header names and for field values. If you use gzip to compress your TSV file, you must use the `.tsv.gz` extension.

TSV Uploader supports parsing files that use either the TSV or CSV file format. 

*CSV Only:* If your files use the CSV file format, prepare the files to conform to the default settings for the [OpenCSV library](http://opencsv.sourceforge.net/apidocs/constant-values.html#au.com.bytecode.opencsv.CSVParser.DEFAULT_STRICT_QUOTES). OpenCSV uses \ (backslash) as the escape character.

## Sample Data Files
Use these sample data files as models for preparing your data for upload:<br>
[NASA Apache web logs](../sample-data#nasa-apache-web-logs)<br>
[Wikipedia web logs](../sample-data#wikipedia-web-logs)<br>
[World Cup 2014 data](../sample-data#world-cup-2014-data)

## Filenames

#### <a name="shard-timerange"></a>Include the shard time range in the filename 
Imhotep partitions your data into shards by the time denoted in the filename. When you [specify a time range in IQL][timerange], Imhotep searches for shards in that range by the time range in the shard name, not the [timestamps in the documents themselves](#time).

You can specify one full day, one full hour, or a range. If you specify a range, the end time is exclusive to the range.
<table>
  <tr>
    <th>Supported formats</th>
    <th>Example</th>
    <th>Description</th>
  </tr>
  <tr>
    <td valign="top">yyyyMMdd</td>
    <td valign="top">`20131201.tsv`</td>
    <td valign="top">The file contains data for one day. </td>
  </tr>
   <tr>
    <td valign="top">yyyyMMdd.HH</td>
    <td valign="top">`20131201.01.tsv`<br>`20131201.02.tsv`</td>
    <td valign="top">The file contains data for one hour of the day: 1-2 AM.<br>The file contains data for one hour of the day: 2-3 AM.</td>
  </tr>
  <tr>
    <td valign="top">yyyyMMdd.HH-yyyyMMdd.HH</td>
    <td valign="top">`20131201.00-20131201.03.tsv`<br>`20140901.00-20140903.00.tsv`</td>
    <td valign="top">The file contains data for the first 3 hours of one day.<br>The file contains data for two full days.</td>
   </tr>
  </table>
  
#### Optional prefixes and suffixes in the filename must be strings
You can add a string prefix or suffix, or both, to your filename. The builder ignores prefixes and suffixes that are strings. For example, the builder correctly ignores `QA_report` and `_combined` in the  `QA_report_20141013_combined.tsv` filename.

Digits are not supported in a prefix or suffix. For example, the `QA_report_20141013_parts1_2.tsv` filename is invalid because it includes digits in the suffix.

## Field Headers

#### The first line of your file represents the header that defines fields in the resulting dataset 

Use field names that contain uppercase `A-Z`, lowercase `a-z`, digits, or `_` (underscore). A field name cannot start with a digit.


#### <a name="time"></a>A document's timestamp must be in the same range as the filename

If the field name is `time` or `unixtime`, the builder parses that field’s values as Unix timestamps and uses them as the document’s timestamps in the dataset. A timestamp can be in seconds or milliseconds since Unix epoch time (UTC). If you don't include a `time` or `unixtime` field, the builder uses the start date of the [shard time range](#shard-timerange) as the timestamp for all of the documents in the file.

NOTE: Do not use floating-point values in a `time` or `unixtime` field, because floating-point values are treated as strings. [Read more](#floating).

By default, all times are GMT-6. 

#### Field names with the * suffix

Adding the `*` suffix to the field name in the header also indexes a tokenized version of that field. 


<table>
  <tr>
    <th>Field Name</th>
    <th>Value</th>
    <th>Indexed Values</th>
  </tr>
  <tr>
    <td valign="top">query*</td>
    <td valign="top">"project manager"</td>
    <td valign="top">query:"project manager"<br>querytok:"project"<br>querytok:"manager"</td>
  </tr>
 
</table>

#### Field names with the ** suffix

Adding the `**` suffix to the field name in the header also indexes bigrams from the field value. 
<table>
  <tr>
    <th>Field Name</th>
    <th>Value</th>
    <th>Indexed Values</th>
  </tr>
  <tr>
    <td valign="top">query**</td>
    <td valign="top">"senior project manager"</td>
    <td valign="top">query:"senior project manager"<br>querytok:"senior"<br>querytok:"project"<br>querytok:"manager"<br>querybigram:"senior project"<br>querybigram:"project manager"</td>
  </tr> 
</table>



## Field Values

#### Prepare the values in your data file

Ensure that you remove tabs and newlines from your values.

*CSV Only:* Do not use quotations around field values. 

####Imhotep has two data types: string and integer(long)
For Imhotep to treat a field’s value as an integer, at least 90% of the values must be integers or blanks, and at least 20% of the total values must be valid integers.

Once set, a field's type must remain consistent. That is, once a field is indexed as an integer, the field must remain an integer. Likewise, if a field is indexed as a string, the field must remain a string. If a field's type is not the same in every shard for that dataset, data for one or the other type will not be accessible through IQL.

#### <a name="floating"></a>Floating-point values become strings

Floating-point values like 1.0 or 1.5 are not supported as integers and are treated as strings. To use floating-point values as metrics, consider the following methods:
<table>
  <tr>
    <th>Method</th>
    <th>Examples</th>
  </tr>
  <tr>
    <td valign="top">Round the values.</td>
    <td valign="top">`1.0 => 1`<br>`1.5 => 2`</td>
   </tr> 
  <tr>
    <td valign="top">Multiply all values by a decimal constant.</td>
    <td valign="top">`1.0 => 10`<br>`1.5 => 15`</td>
   </tr> 
</table>

#### Empty values

An empty value for a string field is indexed as an empty string term and can be queried. For example, `location:""` returns the queries with an empty value for the string field `location`. For integer fields, all non-integer values including the empty value are not indexed and cannot be queried.

[timerange]: {{ site.baseurl }}/docs/timerange
[sample-data]: {{ site.baseurl }}/docs/sample-data