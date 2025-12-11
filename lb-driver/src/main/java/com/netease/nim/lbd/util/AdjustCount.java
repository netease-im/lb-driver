package com.netease.nim.lbd.util;

public class AdjustCount<E> {

	private final E container;
	private final AutoAdjustQueue<E> queue;

	private int count;
	private boolean isQueued = true;
	
	AdjustCount(E container, int initCount, AutoAdjustQueue<E> queue) {
		this.container = container;
		this.count = initCount;
		this.queue = queue;
	}
	
	public int increaseAndGet() {
		queue.incAndAdjust(this);
		return count;
	}
	
	public int decreaseAndGet() {
		queue.decAndAdjust(this);
		return count;
	}
	
	protected void inc() {
		count++;
	}
	
	protected void dec() {
		count--;
	}
	
	public void set(int count) {
		if (this.count != count) {
			this.count = count;
			queue.add(this);
		}
	}
	
	public int get() {
		return count;
	}
	
	public E getContainer() {
		return container;
	}
	
	public void inactive() {
		if (isQueued) {
			queue.remove(this);
			isQueued = false;
		}
	}
	
	public void active() {
		if (!isQueued) {
			queue.add(this);
			isQueued = true;
		}
	}
}
