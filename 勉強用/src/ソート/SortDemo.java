package ソート;

import java.util.Arrays;

public class SortDemo {
	 public static void main(String[] args){

	        int[] data = {7,3,9,2,5,8,1};

	        int[] a1 = data.clone();
	        BubbleSort.sort(a1);
	        System.out.println("BubbleSort  : " + Arrays.toString(a1));

	        int[] a2 = data.clone();
	        SelectionSort.sort(a2);
	        System.out.println("SelectionSort: " + Arrays.toString(a2));

	        int[] a3 = data.clone();
	        InsertionSort.sort(a3);
	        System.out.println("InsertionSort: " + Arrays.toString(a3));

	        int[] a4 = data.clone();
	        MergeSort.sort(a4,0,a4.length-1);
	        System.out.println("MergeSort   : " + Arrays.toString(a4));

	        int[] a5 = data.clone();
	        QuickSort.sort(a5,0,a5.length-1);
	        System.out.println("QuickSort   : " + Arrays.toString(a5));

	        int[] a6 = data.clone();
	        HeapSort.sort(a6);
	        System.out.println("HeapSort    : " + Arrays.toString(a6));

	    }
}
