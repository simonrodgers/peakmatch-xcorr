package com.thaze.peakmatch.processors;

import com.thaze.peakmatch.event.EventException;

/**
 * @author srodgers
 *         created: 14/06/13
 */
public interface Processor {
	void process() throws EventException;
}
