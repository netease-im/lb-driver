package com.netease.nim.lbd.util;

/**
 * AdjustCount
 * @param <E> element
 */
public class AdjustCount<E> {

	private final E container;
	private final AutoAdjustQueue<E> queue;

	private int count;
	private boolean isQueued = true;

	/**
	 * AdjustCount
	 * @param container container
	 * @param initCount initCount
	 * @param queue queue
	 */
	AdjustCount(E container, int initCount, AutoAdjustQueue<E> queue) {
		this.container = container;
		this.count = initCount;
		this.queue = queue;
	}

	/**
	 * increase
	 */
	public void increase() {
		queue.incAndAdjust(this);
	}

	/**
	 * decrease
	 */
	public void decrease() {
		queue.decAndAdjust(this);
	}

	/**
	 * get
	 * @return count
	 */
	public int get() {
		return count;
	}

	/**
	 * getContainer
	 * @return container
	 */
	public E getContainer() {
		return container;
	}

	/**
	 * inactive
	 */
	public void inactive() {
		if (isQueued) {
			queue.remove(this);
			isQueued = false;
		}
	}

	/**
	 * active
	 */
	public void active() {
		if (!isQueued) {
			queue.add(this);
			isQueued = true;
		}
	}

	/**
	 * inc
	 */
	protected void inc() {
		count++;
	}

	/**
	 * dec
	 */
	protected void dec() {
		count--;
	}
}
