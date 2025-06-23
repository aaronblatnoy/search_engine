package prog11;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import prog08.ExternalSort;
import prog08.TestExternalSort;
import prog09.BTree;

public class NotGPT implements SearchEngine {

    public HardDisk pageDisk = new HardDisk(); // maps index (Long) to InfoFile
    public Map<String, String> indexOfURL = new BTree(100); // maps url (String) to index (String)
    public HardDisk wordDisk = new HardDisk(); // maps index (Long) to InfoFile
    public HashMap<String, Long> indexOfWord = new HashMap<>(); // maps word (String) to index (Long)

    Long indexPage(String url){
        Long index = pageDisk.newFile();
        InfoFile infoFile = new InfoFile(url);
        pageDisk.put(index, infoFile);
        indexOfURL.put(url, index.toString());
        System.out.println("indexing url " + url + " index " + index + " file " + infoFile);
        return index;
    }
    Long indexWord(String word){
        Long index = wordDisk.newFile();
        InfoFile infoFile = new InfoFile(word);
        wordDisk.put(index, infoFile);
        indexOfWord.put(word, index);
        System.out.println("indexing word " + word + " index " + index + " file " + infoFile);
        return index;
    }
    @Override
    public void collect(Browser browser, List<String> startingURLs) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for(String url : startingURLs){
            if(indexOfURL.get(url) == null)
                queue.offer(indexPage(url));
        }
        while(!queue.isEmpty()){
            System.out.println("queue " + queue);
            Long currentURLIndex = queue.poll();
            InfoFile file = pageDisk.get(currentURLIndex);
            System.out.println("dequeued " + file);
            if(browser.loadPage(file.data)) {
                List<String> urlList = browser.getURLs();
                System.out.println("urls " + urlList);
                Set<String> setOfURLs = new HashSet<>();
                for(String url : urlList){
                    if(!setOfURLs.contains(url)){
                        setOfURLs.add(url);
                        String stringIndex  = indexOfURL.get(url);
                        if(stringIndex == null) {
                            Long index = indexPage(url);
                            queue.offer(index);
                            file.indices.add(index);
                        } else
                            file.indices.add(Long.parseLong(stringIndex));
                    }
                }
                System.out.println("updated page file " + file);
                List<String> words = browser.getWords();
                Set<String> wordsSet = new HashSet<>();
                System.out.println("words " + words);
                for(String word : words){
                    if(!wordsSet.contains(word)){
                        wordsSet.add(word);
                        Long wordIndex = indexOfWord.get(word);
                        if(wordIndex == null)
                            wordIndex = indexWord(word);
                        InfoFile wordFile = wordDisk.get(wordIndex);
                        wordFile.indices.add(currentURLIndex);
                        wordDisk.put(wordIndex,wordFile);
                        System.out.println("updated word " + word + " index " + wordIndex + " file " + wordFile);
                    }
                }
            }
        }
    }
    @Override
    public void rank(boolean fast) {
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.impact = 1;
            file.impactTemp = 0;
        }
        if(fast){
            for(int i = 0; i < 20; i++)
                rankFast();
        } else{
            for(int i = 0; i < 20; i++)
                rankSlow();
        }
    }
    void rankSlow() {
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            double impactPerIndex = file.impact/file.indices.size();
            for(Long linkedIndex : file.indices){
                InfoFile linkedFile = pageDisk.get(linkedIndex);
                linkedFile.impactTemp += impactPerIndex;
            }
        }
        double zeroLinkedImpact = 0.0;
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            if(file.indices.isEmpty()) {
                zeroLinkedImpact += file.impact;
            }
        }
        zeroLinkedImpact /= pageDisk.entrySet().size();
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.impact = file.impactTemp + zeroLinkedImpact;
            file.impactTemp = 0.0;
        }


    }
    void rankFast() {
        double zeroLinkedImpact = 0.0;
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            if(file.indices.isEmpty()) {
                zeroLinkedImpact += file.impact;
            }
        }
        zeroLinkedImpact /= pageDisk.entrySet().size();
        try {
            PrintWriter out = new PrintWriter("unsorted-votes.txt");
            for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
                long index = entry.getKey();
                InfoFile file = entry.getValue();
                double impactPerIndex = file.impact/file.indices.size();
                for(Long linkedIndex : file.indices){
                    Vote vote = new Vote(linkedIndex, impactPerIndex);
                    out.println(vote.toString());
                }
            }
            out.close();
            VoteScanner voteScanner = new VoteScanner();
            ExternalSort<Vote> externalSort = new ExternalSort<>(voteScanner);
            externalSort.sort("unsorted-votes.txt", "sorted-votes.txt");
        } catch (FileNotFoundException e) {
            System.out.println(e);
        }
        VoteScanner sortedVoteScanner = new VoteScanner();
        Iterator<Vote> sortedVoteIterator = sortedVoteScanner.iterator("sorted-votes.txt");
        Vote nextVote = sortedVoteIterator.next();
        for (Map.Entry<Long,InfoFile> entry : pageDisk.entrySet()) {
            long index = entry.getKey();
            InfoFile file = entry.getValue();
            file.impact = zeroLinkedImpact;
            while(nextVote.index == index){
                file.impact += nextVote.vote;
                if(sortedVoteIterator.hasNext())
                    nextVote = sortedVoteIterator.next();
                else
                    break;
            }
        }
    }
    @Override
    public String[] search(List<String> searchWords, int numResults) {
        Iterator<Long>[] pageIndexIterators = (Iterator<Long>[]) new Iterator[searchWords.size()];
        long[] currentPageIndices = new long[searchWords.size()];
        for(int i = 0; i < searchWords.size(); i++){
            Iterator<Long> iter = wordDisk.get(indexOfWord.get((searchWords.get(i)))).indices.iterator();
            pageIndexIterators[i] = iter;
        }
        PriorityQueue<Long> bestPageIndices = new PriorityQueue<>(new PageIndexComparator());
        while(getNextPageIndices(currentPageIndices, pageIndexIterators)){
            if(allEqual(currentPageIndices)) {
                System.out.println(pageDisk.get(currentPageIndices[0]).data);
                if(bestPageIndices.size() < numResults)
                    bestPageIndices.offer(currentPageIndices[0]);
                else if(pageDisk.get(currentPageIndices[0]).impact > pageDisk.get(bestPageIndices.peek()).impact){
                    bestPageIndices.poll();
                    bestPageIndices.offer(currentPageIndices[0]);
                }
            }
        }
        String[] result = new String[bestPageIndices.size()];
        for(int i = result.length-1; i >= 0; i--)
            result[i] = pageDisk.get(bestPageIndices.poll()).data;
        return result;
    }
    private boolean allEqual(long[] array) {
        for (int i = 1; i < array.length; i++) {
            if (array[i] != array[0])
                return false;
        }
        return true;
    }
    private long getLargest(long[] array){
        long largest = array[0];
        for(int i = 1; i < array.length; i++){
            if(array[i] > largest)
                largest = array[i];
        }
        return largest;
    }
    private boolean getNextPageIndices
            (long[] currentPageIndices, Iterator<Long>[] pageIndexIterators) {
        if(allEqual(currentPageIndices)){
            for(int i = 0; i < currentPageIndices.length; i++){
                if(!pageIndexIterators[i].hasNext())
                    return false;
                currentPageIndices[i] = pageIndexIterators[i].next();
            }
        } else {
            long largest = getLargest(currentPageIndices);
            for(int i = 0; i < currentPageIndices.length; i++){
                if(currentPageIndices[i] != largest) {
                    if(!pageIndexIterators[i].hasNext())
                        return false;
                    currentPageIndices[i] = pageIndexIterators[i].next();
                }
            }
        }
        return true;
    }
    public class Vote implements Comparable<Vote>{
        Long index;
        double vote;
        public Vote (Long index, double vote){
            this.index = index;
            this.vote = vote;
        }

        @Override
        public int compareTo(Vote o) {
            if(index.compareTo(o.index) != 0)
                return index.compareTo(o.index);
            else
                return Double.compare(vote, o.vote);
        }

        public String toString(){
            return index + " " + vote;
        }
    }
    class VoteScanner implements ExternalSort.EScanner<Vote> {
        class Iter implements Iterator<Vote> {
            Scanner in;

            Iter (String fileName) {
                try {
                    in = new Scanner(new File(fileName));
                } catch (Exception e) {
                    System.out.println(e);
                }
            }

            public boolean hasNext () {
                return in.hasNext();
            }

            public Vote next () { return new Vote(in.nextLong(), in.nextDouble()); }
        }

        public Iterator<Vote> iterator (String fileName) { return new NotGPT.VoteScanner.Iter(fileName); }
    }
    class PageIndexComparator implements Comparator<Long> {
        @Override
        public int compare(Long o1, Long o2) {
            return Double.compare(pageDisk.get(o1).impact, pageDisk.get(o2).impact);
        }
    }
}
