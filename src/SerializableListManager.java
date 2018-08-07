import java.awt.*;
import java.io.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.util.Arrays;

class SerializableListManager {

    private myCustomArrayList[] myList;
    private final int RESIZE_CONSTANT;
    private long MEMORY_LEFT = 0; //NOTE this is a static variable
    private final static String SAVE_DIRECTORY = "/tmp/";

    //Weird prefix name to make sure no such file already exists in the /tmp/ folder
    private final static String SAVE_FILE_NAME_PREFIX = "zlgKh23Gkj1hg_REC_SYSTEM";

    private final static String SAVE_FILE_NAME = SAVE_DIRECTORY + SAVE_FILE_NAME_PREFIX;


    SerializableListManager(int numOfLists, int resize_constant) {
        deleteAllFilesMatchingPrefix();
        this.RESIZE_CONSTANT = resize_constant;
        myList = new myCustomArrayList[numOfLists];

        for(int i=0; i<myList.length; i++) {
            myList[i] = new myCustomArrayList(i, i);
        }
    }

    void addElementToList(int listIndex, short element){
        myList[listIndex].add(element);
    }

    short getElementFromList(int listIndex, int elementIndex){
        return myList[listIndex].get(elementIndex);
    }

    //This frees memory
    void deleteList(int listIndex){
        MEMORY_LEFT += myList[listIndex].array.length;
        myList[listIndex] = null;
    }

    //This method deletes all files in a directory matching the PREFIX_FILE_NAME
    //This is only called as an assurance that no files are there matching the name, since the programs appends
    //files and there is a chance files from a previous execution of the program are still there, in case the program
    //was not allowed to complete its execution (i.e. CTRL+C). Note normally when the program is successfully executed
    //no temporary files are to remain
    private void deleteAllFilesMatchingPrefix(){
        long time = -System.currentTimeMillis();
        int deletedFiles = 0;
        File dir = new File(SAVE_DIRECTORY);
        File[] files = dir.listFiles();
        for(File f : files){
            if(f.getName().contains(SAVE_FILE_NAME_PREFIX)){
                f.delete();
                deletedFiles++;
            }
        }
        time +=System.currentTimeMillis();
        System.out.println("Deleted " + deletedFiles + " temp files from a previous interrupted execution of the program.");
        System.out.println("File deletion took " + time/1000 + " seconds.");
    }

    private class myCustomArrayList{

        private short[] array;
        private int currentSize = 0;
        private int maxResize;

        private final String saveFileName;

        private short[] fullArray;

        // this is an index that counts how many cells are in the disk, alternativelly it can be seen
        // as the first index of the array that is in the RAM
        // To give an example, this arraylist has 5 elements already saved in the disk, and 5 elements in the RAM
        // The 6th 7th 8th 9th 10th element is in the RAM, then the arrayInMemoryStartCount will be equal to 6
        private int arrayInMemoryStartCount = 0;

        myCustomArrayList(int maxResize, int saveID){
            array = new short[Math.min(maxResize, RESIZE_CONSTANT)];
            this.maxResize = maxResize;
            this.saveFileName = SAVE_FILE_NAME+saveID+".tmp";
        }

        void add(short element){
            if(currentSize == array.length){
                //If I have free memory resize instead of saving to disk
                if(MEMORY_LEFT > 0) {
                    // New size is either the min between the total memory still required to reach
                    // maxResize OR the current size plus the memory_left
                    int newSize = (int) Math.min(maxResize-arrayInMemoryStartCount, array.length + MEMORY_LEFT);

                    // The new MEMORY_LEFT is the previous MEMORY_LEFT minus the memory used in this array
                    MEMORY_LEFT = array.length + MEMORY_LEFT - newSize;

                    short[] biggerArray = new short[newSize];
                    System.arraycopy(array, 0, biggerArray, 0, array.length);
                    array = biggerArray;
                }else{
                    //I cannot resize because I do not have any memory left
                    //Serialize to disk!
                    saveToFile(this.array);
                    arrayInMemoryStartCount += this.array.length;
                    //set the array length to 0
                    currentSize = 0;

                }
            }
            array[currentSize++] = element;
        }

        short get(int index) {
            //Its in the RAM
            if (index >= this.arrayInMemoryStartCount) {
                return array[index - this.arrayInMemoryStartCount];
            } else { //It was in the disk
                if (this.fullArray == null) {
                    loadFromFile();
                }
                return this.fullArray[index];
            }
        }

        void saveToFile(short[] array){
            try{
                File file = new File(this.saveFileName);
                RandomAccessFile raf= new RandomAccessFile(file, "rw");
                FileChannel fc = raf.getChannel();
                // "channel.size" sets the pointer there, This appends the current file
                ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, fc.size(), 2*array.length);
                for(int i=0; i<array.length; i++){
                    buffer.putShort(array[i]);
                }
                fc.close();
                raf.close();
            }catch (IOException e) {
                System.out.println("Couldn't serialize!");
                e.printStackTrace();
                System.out.println(e);
                System.exit(-4);
            }
        }

        //This is supposed to only be called once per array by the program
        void loadFromFile(){
            try {
                File file = new File(this.saveFileName);
                FileInputStream in = new FileInputStream(file);
                FileChannel fc = in.getChannel();
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                int arraySize = (int)fc.size()/2;
                this.fullArray = new short[arraySize];
                for(int i=0; i<arraySize; i++){
                    this.fullArray[i] = buffer.getShort();
                }

                file.delete();
                in.close();
                fc.close();
            }catch(IOException e){
                e.printStackTrace();
                System.out.println("Couldn't deserialize");
                System.out.println(e);
                System.exit(-5);
            }
        }

    }

}
