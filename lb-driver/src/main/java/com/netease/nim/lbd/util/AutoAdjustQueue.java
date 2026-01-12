package com.netease.nim.lbd.util;

import java.util.*;

/**
 * AutoAdjustQueue
 * @param <E> element
 */
public class AutoAdjustQueue<E> {
	
	private final Map<E, Node> index = new HashMap<>();
	
	private Node head;
	
	private Node tail;
	
	private int countSum = 0;
	
	private int size = 0;
	
	private final Random random = new Random(System.currentTimeMillis());

	/**
	 * AutoAdjustQueue
	 */
	public AutoAdjustQueue() {
	}

	/**
	 * createCountElement
	 * @param container container
	 * @return AdjustCount
	 */
	public AdjustCount<E> createCountElement(E container) {
		AdjustCount<E> count = new AdjustCount<E>(container, 0, this);
		Node node = new Node(count);
		if (head != null) {
			head.before = node;
			node.next = head;
		} else {
			tail = node;
		}
		head = node;
		index.put(container, node);
		size++;
		return count;
	}

	/**
	 * add
	 * @param element element
	 */
	public void add(AdjustCount<E> element) {
		if (index.containsKey(element.getContainer())) {
			return;
		}
		size++;
		countSum += element.get();
		try {
			Node node = new Node(element);
			index.put(element.getContainer(), node);
			if (head == null) {
				head = node;
				tail = node;
				return;
			}
			int count = element.get();
			int evalHead = count - head.count.get();
			int evalTail = count - tail.count.get();
			if (evalHead <= 0 || evalHead + evalTail < 0) {
				node.next = head;
				head.before = node;
				head = node;
			} else {
				node.before = tail;
				tail.next = node;
				tail = node;
			}
			if (evalHead > 0 || evalTail < 0) {
				adjust(element);
			}
		} finally {
			checkSum();
		}
	}

	/**
	 * remove
	 * @param element element
	 */
	public void remove(AdjustCount<E> element) {
		Node node = index.remove(element.getContainer());
		if (node != null)  {
			Node before = node.before;
			Node next = node.next;
			if (before != null) {
				before.next = next;
			} else {
				head = next;
			}
			if (next != null) {
				next.before = before;
			} else {
				tail = before;
			}
			size--;
			countSum -= element.get();
			checkSum();
		}
	}

	/**
	 * peekHead
	 * @return head
	 */
	public E peekHead() {
		if (head != null) {
			return head.count.getContainer();
		}
		return null;
	}

	/**
	 * peekHeadRandomly
	 * @return element
	 */
	public E peekHeadRandomly() {
		if (head != null) {
			int headCount = head.count.get();
			int nodeCount = 1;
			for (Node current = head.next; current != null; current = current.next) {
				if (current.count.get() == headCount) {
					nodeCount++;
				} else {
					break;
				}
			}
			Node pickNode = head;
			if (nodeCount > 1) {
				int pickPos = random.nextInt(nodeCount);
				if (pickPos > 0) {
					for (int i = 0; i < pickPos; i++) {
						if (pickNode.next != null) {
							pickNode = pickNode.next;
						} else {
							break;
						}
					}
				}
			}
			return pickNode.count.getContainer();
		}
		return null;
	}

	/**
	 * peekTail
	 * @return element
	 */
	public E peekTail() {
		if (tail != null) {
			return tail.count.getContainer();
		}
		return null;
	}

	/**
	 * peekHeadExcludeRandomly
	 * @param excludes excludes
	 * @return element
	 */
	public E peekHeadExcludeRandomly(Set<E> excludes) {
		if (excludes == null || excludes.isEmpty()) {
			return peekHeadRandomly();
		}
		if (excludes.size() == size) {
			return null;
		}
		List<E> candicateList = new ArrayList<>();
		Integer candicateCount = null;
		for (Node current = head; current != null; current = current.next) {
			E e = current.count.getContainer();
			if (excludes.contains(e)) {
				continue;
			}
			if (candicateCount == null) {
				candicateCount = current.count.get();
			}
			if (current.count.get() == candicateCount) {
				candicateList.add(e);
			} else {
				break;
			}
		}
		if (!candicateList.isEmpty()) {
			return candicateList.get(random.nextInt(candicateList.size()));
		}
		return null;
	}

	/**
	 * peekHeadExclude
	 * @param excludes excludes
	 * @return element
	 */
	public E peekHeadExclude(Set<E> excludes) {
		if (excludes == null || excludes.isEmpty()) {
			return peekHead();
		}
		if (excludes.size() == size) {
			return null;
		}
		for (Node current = head; current != null; current = current.next) {
			E e = current.count.getContainer();
			if (excludes.contains(e)) {
				continue;
			}
			return e;
		}
		return null;
	}

	/**
	 * peekTailExclude
	 * @param excludes excludes
	 * @return element
	 */
	public E peekTailExclude(Set<E> excludes) {
		if (excludes == null || excludes.isEmpty()) {
			return peekTail();
		}
		if (excludes.size() == size) {
			return null;
		}
		for (Node current = tail; current != null; current = current.before) {
			E e = current.count.getContainer();
			if (excludes.contains(e)) {
				continue;
			}
			return e;
		}
		return null;
	}

	/**
	 * check isCountBalanced
	 * @return true/false
	 */
	public boolean isCountBalanced() {
		if (head == null || tail == null || size <= 1) {
			return true;
		}
		return head.count.get() >= (tail.count.get() - 1);
	}

	/**
	 * get size
	 * @return size
	 */
	public int getSize() {
		return size;
	}

	/**
	 * getCountSum
	 * @return countSum
	 */
	public int getCountSum() {
		return countSum;
	}

	/**
	 * incAndAdjust
	 * @param element element
	 */
	public void incAndAdjust(AdjustCount<E> element) {
		element.inc();
		if (adjust(element)) {
			countSum++;
		}
		checkSum();
	}

	/**
	 * decAndAdjust
	 * @param element element
	 */
	public void decAndAdjust(AdjustCount<E> element) {
		element.dec();
		if (adjust(element)) {
			countSum--;
		}
		checkSum();
	}
	
	private void checkSum() {
		int count = 0;
		Node node = head;
		while (node != null) {
			count += node.count.get();
			node = node.next;
		}
		if (count != countSum) {
			throw new IllegalStateException("checksum illegal");
		}
	}
	
	private boolean adjust(AdjustCount<E> element) {
		Node node = index.get(element.getContainer());
		if (node != null) {
			int count = element.get();
			for (;;) {
				if (head != node && node.before.count.get() > count) {
					swapBefore(node);
					continue;
				}
				if (tail != node && node.next.count.get() < count) {
					swapNext(node);
					continue;
				}
				break;
			}
			return true;
		} else {
			return false;
		}
	}
	
	private void swapBefore(Node node) {
		if (node == head || node.before == null) {
			throw new IllegalStateException("Head element can not swap before element");
		}
		Node before = node.before;
		Node next = node.next; 
		Node beforeOfBefore = before.before;
		if (beforeOfBefore != null) {
			beforeOfBefore.next = node;
			node.before = beforeOfBefore;
		} else {
			head = node;
			node.before = null;
		}
		before.next = next;
		if (next != null) {
			next.before = before;
		} else {
			tail = before;
		}
		node.next = before;
		before.before = node;
	}
	
	private void swapNext(Node node) {
		if (node == tail || node.next == null) {
			throw new IllegalStateException("Tail element can not swap next element");
		}
		Node before = node.before;
		Node next = node.next; 
		Node nextOfNext = next.next;
		if (nextOfNext != null) {
			nextOfNext.before = node;
			node.next = nextOfNext;
		} else {
			tail = node;
			node.next = null;
		}
		next.before = before;
		if (before != null) {
			before.next = next;
		} else {
			head = next;
		}
		node.before = next;
		next.next = node;
	}
	
	private class Node {
		AdjustCount<E> count;
		Node before;
		Node next;
		
		public Node(AdjustCount<E> count) {
			this.count = count;
		}
	}
}
