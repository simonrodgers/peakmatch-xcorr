Efficient bulk cross correlation of time series
===============================================

Process for efficiently calculating cross correlation (xcorr) of a collection of time-series data (events), 
emitting only 'matches' (pairs of events which have an xcorr above a certain threshold). The match density is 
assumed to be sparse - less than ~ 1/100 event pairs should match for this process to be useful.

n = number of events to be cross correlated
m = number of data points per event

Brute force xcorr between two events involves taking the dot product of two time series vectors, 
for each possible alignment between the two vectors. this leads to O(m^2) performance per event pair, 
and hence O(n^2 * m^2) for performing all. this is impractical for large n.

FFT-based xcorr is much faster than performing brute force xcorr - transforming into frequency space means that 
only O(m) operations are required per xcorr step. however, attempting a full calculation of all possible event pairs 
still takes O(m * n^2) performance, which is still prohibitively expensive for large n (> 10^4)

This process first uses a fast approximation stage, performed across all n^2 / 2 pairs. this produces a list of candidate event pairs, the length of which is << n^2. 
These candidate pairs are then cross-correlated using the normal FFT xcorr method to reject false positives.

The fast approximation stage (peakmatch) uses the following techniques:

1) Top peak alignment: the time series peaks and troughs for each event are pre-calculated, and pre-ordered by amplitude. 
when performing the peakmatch stage, only the top K peaks / troughs are aligned - O(K^2) - instead of every single possible alignment - O(m). 
for a suitably small K (K << M^1/2 - K typically 2 or 3) this provides performance benefits, 

2) Sampling: calculate the dot product of the peak-aligned only every S data points. this speeds up the algorithm by a factor of S, 
again to be traded against a small accuracy penalty.

3) Top amplitude threshold: only calculate dot product components for data points where the amplitude is higher than a fraction F of the peak amplitude. 
the final xcorr value for a well-matching event pair is dominated by the overlaps of the peaks / troughs (eg 1000 * 1000 >> 50 * 50 ), 
but the bulk of the calculation work is dominated by the 'noise' - areas where the amplitude is relatively small in comparison to the peak amplitude. 
If these noise values are ignored when calculating dot products, there is a significant performance gain, again to be traded against a small accuracy penalty.

These optimisations result in a certain degree of error: a certain number of false positives (non-matching pairs - above match threshold - 
presented as candidates) and false negatives (matching pairs missing from candidates list). False positives are all discarded in the post-process phase, 
but false negatives will be discarded permanently. This is why the analysis phase is necessary - by varying the control parameters, generally 
as the false positives increase, the false negatives decrease - but a higher false positive rate increases the post-process time as there's more full 
FFT processing to be done, which is relatively slow. The user is advised to tune the parameters until a satisfactory trade-off between running time 
and accuracy is reached.

This approximation step, with appropriately chosen parameters, typically takes in the order of 1-2 microseconds (experiments performed on an Intel(R) Core(TM)2 Duo CPU T9550 @ 2.66GHz) 
per event pair, depending on suitable constants chosen in the analysis stage of the process. this compares to tens of milliseconds to perform an accurate FFT xcorr.

The process has four phases:

Analysis phase
--------------

- Performs a full process run on a relatively small sample (~1000 events, to be prepared by user)
- analyses the accuracy (false positive and false negative rate)
- analyses the performance (extrapolates estimate to full data set)

parameters to vary:
	- top-n-peaks
		top N peaks (both min and max), ordered by amplitude, which will be aligned together
		run-time varies as O(top-n-peaks ^ 2) - every event's top N peaks are aligned against every other event's top N peaks
	- sampling-stride
		sample events every sampling-stride entries
		run-time varies as O(1/sampling-stride)
	- top-amplitude-threshold
		only calculate xcorr values for values where the amplitude is higher than this fraction of the peak amplitude
		the final xcorr value for a well-matching pair is dominated by the multiplication of two peaks together (eg 1000 * 1000 >> 50 * 50)
		setting this to a non-zero value means eliminating a large amount of noise from the calculations, and drastically speeding up the calculation
		tweak this value in ANALYSE phase: too high -> false negatives. too low -> performance degredation
	- candidate-threshold
		threshold for candidates to be included
		this will be lower than the final xcorr calculated value
		tweak this value in ANALYSE phase: too high -> false negatives. too low -> too many false positives for post-processing
	- final-threshold
		threshold for the final FFT xcorr post-process step

Peakmatch phase
---------------
Having chosen suitable parameter in the Analysis phase, perform the 


FFT Precache phase


Postprocess phase




Operation
---------
- create two directories, for full and sample datasets
- put all events into full
- ensure all events are expected-file-line-count lines long
	(find /path/to/full -name "*.dat" | xargs wc -l | grep -v 10240 | grep -v total | awk '{print $2}' | xargs -I xx mv xx /path/to/wrong-size/dir)
- take small (~500-1000) random sample from full into sample
 	(ls /path/to/full | shuf | head -500 | xargs -I xx cp /path/to/full/xx /path/to/sample)
- edit xcorr.conf, add locations of dataset directories
- initial parameter refinement - set mode=ANALYSE, compile, run (see below), observe output
- change parameters in config file until performance / accuracy tradeoff is acceptable:
- set mode=PEAKMATCH
- set mode=FFTPRECACHE, run
- set mode=POSTPROCESS, run

Prerequisites
-------------
java 1.7
maven 2

Dependencies (via maven)
------------------------
apache commons-lang
apache commons-math
google guava
joda-time
Chronicle (see https://github.com/peter-lawrey/Java-Chronicle)
net.sf.json-lib

Compilation
-----------
mvn assembly:single

Usage
-----
java -Xmx2048M -jar target/peakmatch-jar-with-dependencies.jar

(change 2048M to whatever memory limits you want to give the process)