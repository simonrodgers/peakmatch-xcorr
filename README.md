Efficient bulk cross correlation of time series
===============================================
Process for efficiently calculating cross correlation (xcorr) of a collection of time-series data (events), emitting only 'matches' (pairs of events which have an xcorr above a certain threshold). The match density is assumed to be sparse - less than ~ 1/100 event pairs should match for this process to be useful.

* N = number of events to be cross correlated
* M = number of data points per event

Brute force xcorr between two events involves taking the dot product of two time series vectors, for each possible alignment between the two vectors. this leads to O(M^2) performance per event pair, and hence O(N^2 x M^2) for performing all pair calculations. this is impractical for large N.

FFT-based xcorr is much faster than performing brute force xcorr - transforming into frequency space means that only O(M) operations are required per xcorr step. however, attempting a full calculation of all possible event pairs still takes O(M x N^2) performance, which is still prohibitively expensive for large N (eg > 10^4)

This process first uses a fast approximation stage, performed across all N^2 / 2 pairs. this produces a list of candidate event pairs, the length of which is << N^2. These candidate pairs are then cross-correlated using the normal FFT xcorr method to reject false positives.

The fast approximation stage (peakmatch) uses the following techniques:

* *Top peak alignment* : the time series peaks and troughs for each event are pre-calculated, and pre-ordered by amplitude. when performing the peakmatch stage, only the top K peaks / troughs are aligned - O(K^2) - instead of every single possible alignment - O(m). for a suitably small K (K << M^1/2 - K typically 2 or 3) this provides performance benefits, 

* *Sampling* : calculate the dot product of the peak-aligned only every S data points. this speeds up the algorithm by a factor of S,  again to be traded against a small accuracy penalty.

* *Top amplitude threshold* : only calculate dot product components for data points where the amplitude is higher than a fraction F of the peak amplitude. the final xcorr value for a well-matching event pair is dominated by the overlaps of the peaks / troughs (eg 1000 * 1000 >> 50 * 50 ), but the bulk of the calculation work is dominated by the 'noise' - areas where the amplitude is relatively small in comparison to the peak amplitude. If these noise values are ignored when calculating dot products, there is a significant performance gain, again to be traded against a small accuracy penalty. 

These optimisations result in a certain degree of error: a certain number of false positives (non-matching pairs - above match threshold - presented as candidates) and false negatives (matching pairs missing from candidates list). False positives are all discarded in the post-process phase, but false negatives will be discarded permanently. This is why the analysis phase is necessary - by varying the control parameters, generally as the false positives increase, the false negatives decrease - but a higher false positive rate increases the post-process time as there's more full FFT processing to be done, which is relatively slow. The user is advised to tune the parameters until a satisfactory trade-off between running time and accuracy is reached. 

The approximation step, with appropriately chosen parameters, typically takes in the order of 1-2 microseconds (experiments performed on an Intel(R) Core(TM)2 Duo CPU T9550 @ 2.66GHz) per event pair, depending on suitable constants chosen in the analysis stage of the process. this compares to tens of milliseconds to perform an accurate FFT xcorr.

The process has four phases:

1 - Analysis phase
--------------
* Performs a full process run on a relatively small sample (~1000 events, to be prepared by user) - peakmatch and FFT xcorr
* analyses the accuracy (false positive and false negative rate)
* analyses the performance (extrapolates estimate to full data set)

Parameters are set in the config file `xcorr.conf`:
* top-k-peaks
	* top K peaks (both min and max), ordered by amplitude, which will be aligned together. 
	* run-time varies as O(K^2) - every event's top K peaks are aligned against every other event's top K peaks
	
* sampling-stride
	* sample events every sampling-stride entries
	* run-time varies as O(1 / sampling-stride)
	
* top-amplitude-threshold
	* only calculate xcorr values for values where the amplitude is higher than this fraction of the peak amplitude.
	* the final xcorr value for a well-matching pair is dominated by the multiplication of two peaks together (eg 1000 * 1000 >> 50 * 50).
	* setting this to a non-zero value means eliminating a large amount of noise from the calculations, and drastically speeding up the calculation. 
	* tweak this value in ANALYSE phase: too high -> false negatives. too low -> performance degredation
	
* candidate-threshold
	* threshold for candidates to be included
	* this will be lower than the final xcorr calculated value
	* tweak this value in ANALYSE phase: too high -> false negatives. too low -> too many false positives for post-processing

* final-threshold
	* threshold for the final FFT xcorr post-process step

inputs:
* sample data set - all files in the `dataset.sample` directory

outputs:
* `xcorr.saved` - cached N^2 FFT xcorr values of sample events - used for false negative / positive analysis, cached because even a sample of ~1000 is relatively slow to calculate, especially if iterating parameter values vs. performance/accuracy
* analysis written to the console

2 - Peakmatch phase
---------------
Having chosen suitable parameters in the Analysis phase, generate approximate candidates for subsequent post-processing across the full data set. emit only candidates whose approximated xcorr value is higher than `candidate-threshold`. This figure will necessarily be lower than the real xcorr value and the threshold should accordingly be set lower than `final-threshold`.

inputs:
* full data set - all files in the `dataset.full` directory

outputs:
* `xcorr.candidates` - one line per match above candidate-threshold, tab-separated: `event A filename <tab> event B filename <tab> match value`

while this is running, progress is printed to the console together with the projected finish time.

3 - FFT Precache phase
------------------
Fourier transform cross correlation process between normalised events A and B:

1. Calculate FT of A
2. Reverse B
3. Calculate FT of B_reverse
4. Calculate complex dot product of FT vectors
5. Calculate inverse FT of the dot product
6. Return the real components of the inverse FT, the highest value is the cross-correlation value of the two events.

Steps 1, 2 and 3 are pre-calculated for each event to save a significant amount of time per calculation step. However, they occupy a large amount of memory (significantly more than for peakmatch - 3000 events took more than 2GB). This means that for large values of n they cannot be held in memory together on any reasonable size machine. To ensure fast loading, they are pre-calculated and put in a large [memory-mapped file](http://en.wikipedia.org/wiki/Memory-mapped_file) - this can be arbitrary size (up to the size of the disk), occupies negligible heap memory, and takes advantage of the operating system page cache to return the pre-calculated complex FT vectors for an event.

This means that as much system memory as possible should be left free for the page cache during this and the subsequent phase.

Note: must be run on a 64-bit system if running on more than a few thousand events, or it will exhaust addressable space.

inputs:
* full data set - all files in the `dataset.full` directory

outputs:
* `fftcache.chronicle.data` - memory mapped chronicle data file
* `fftcache.chronicle.index` - memory mapped chronicle index
* `fftcache.index.json` - dictionary of event filename to chronicle numeric ID

while this is running, progress is printed to the console together with the projected finish time.

4 - Postprocess phase
-----------------
Having pre-calculated and stored the FFT values for each event, perform full FFT cross correlation on each candidate, emitting only pairs whose FFT xcorr value is higher than the `final-threshold` config value

inputs:
* `xcorr.candidates` - output from peakmatch phase

outputs:
* `xcorr.postprocess` - same format as `xcorr.candidates`, one tab-separated line per validated candidate pair and their FFT xcorr value

while this is running, progress is printed to the console together with the projected finish time.

Data format
-----------
Expected data file format - one file per event, containing one line per data point, a single ascii-encoded floating point value 

eg:

	-0.503178
	-2.849900
	-6.152631
	-9.755478
	-12.723866
	-14.161493
	...
	
Operation
---------
* create two directories, for full and sample datasets
* put all events into full
* ensure all event files have expected-file-line-count lines:

		find /path/to/full -name "*.dat" | xargs wc -l | grep -v 10240 | grep -v total | awk '{print $2}' | xargs -I xx mv xx /path/to/wrong-size/dir

* take small (~1000) random sample from full into sample:

		ls /path/to/full | shuf | head -1000 | xargs -I xx cp /path/to/full/xx /path/to/sample

* edit xcorr.conf, add locations of dataset directories
* initial parameter refinement - set mode=ANALYSE, compile, run (see below), observe output
* change parameters in config file until performance / accuracy tradeoff is acceptable:
* set mode=PEAKMATCH, run
* set mode=FFTPRECACHE, run
* set mode=POSTPROCESS, run

Prerequisites
-------------
* java 1.7
* maven 2

Dependencies (via maven)
------------------------
* apache commons-lang
* apache commons-math
* google guava
* joda-time
* chronicle (see https://github.com/peter-lawrey/Java-Chronicle)
* net.sf.json-lib

Compilation
-----------
    mvn assembly:single

Usage
-----
    java -Xmx2048M -jar target/peakmatch-jar-with-dependencies.jar

Change 2048M to whatever memory limits you want to give the process - but do not use more than necessary for the FFT precache and post-process steps, the system page cache should be given as much as possible (see size of fftcache.chronicle.data file after FFT precache)

Note: must be run on a 64-bit system if running on more than a few thousand events, or the FFT precache and post-process steps will exhaust addressable space.
