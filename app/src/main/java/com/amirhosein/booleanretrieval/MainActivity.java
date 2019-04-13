package com.amirhosein.booleanretrieval;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.logging.XMLFormatter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static android.os.Process.killProcess;
import static android.os.Process.setThreadPriority;

public class MainActivity extends AppCompatActivity {

    Toolbar toolbar;
    AppCompatEditText edt_search;
    AppCompatTextView txt_results;
    AppCompatImageButton imgb_search;
    RecyclerView rcl;
    RecyclerView.LayoutManager lmg;
    DocRecyclerViewAdapter docRecyclerViewAdapter;
    List<DocItem> docItemList = new ArrayList<>();
    List<BitSet> bitSetList;
    List<String> words;
    ProgressDialog progressDialog;

    String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewsById();


        prepareQuerying(false);
    }

    //**********************************************************************************************

    private void findViewsById() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        edt_search = findViewById(R.id.edt_search);
        edt_search.setText("bmw AND (excellent OR good)");
        txt_results = findViewById(R.id.txt_results);
        txt_results.setText("");
        imgb_search = findViewById(R.id.imgb_search);
        rcl = findViewById(R.id.rcl);
        lmg = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        rcl.setLayoutManager(lmg);
        docRecyclerViewAdapter = new DocRecyclerViewAdapter(this, docItemList);
        rcl.setAdapter(docRecyclerViewAdapter);


        docRecyclerViewAdapter.setListener(new DocRecyclerViewAdapterListener() {
            @Override
            public void onClick(int position) {
                DocViewFragment docViewFragment = new DocViewFragment();
                docViewFragment.docItem = docItemList.get(position);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.mainactivity_parent, docViewFragment)
                        .addToBackStack("docViewFragment")
                        .commit();
            }
        });


        imgb_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = edt_search.getText().toString();
                parseAndRunQuery(query);
            }
        });
    }

    Stack stack = new Stack();
    private void parseAndRunQuery(String query) {

        long start = System.currentTimeMillis();

        Log.d("YaMahdi", "query is: "+query);
        query = query.replaceAll("\\(", " ( ");
        query = query.replaceAll("\\)", " ) ");
        query = query.replaceAll("\\s+", " ");
        query = query.replaceAll(" ~ ", " NOT ");
        query = query.replaceAll(" not ", " NOT ");
        query = query.replaceAll(" & ", " AND ");
        query = query.replaceAll(" && ", " AND ");
        query = query.replaceAll(" and ", " AND ");
        query = query.replaceAll(" \\| ", " OR ");
        query = query.replaceAll(" \\|\\| ", " OR ");
        query = query.replaceAll(" or ", " OR ");
        query = query.replaceAll("[/_\",.+*\\-&\\[\\]{}�!?$@<~>:=;%#\\\\]", "");
        Log.d("YaMahdi", "query change to: "+query);
        String[] sentences = query.split(" ");

        for (int i=0; i<sentences.length; i++) {
            if (is_word(sentences[i])) {
                String norm = normalWord(sentences[i]);
                if (norm.equals("")) norm = "*";
                sentences[i] = norm;
            }
            Log.d("YaMahdi", "befor infix2postfix: "+sentences[i]);
        }
        String[] result = infixToPostfix2(sentences);
        for (int i=0; i<result.length; i++) {
            Log.d("YaMahdi", "after infix2postfix: "+result[i]);
        }


        BitSet bitSet = calculate(result);

        List<Integer> retDocs = new ArrayList<>();
        for (int i = 0; i < bitSet.length(); i++) {
            if (bitSet.get(i))
                retDocs.add(i);
        }
        createDocItemList(retDocs);

        long end = System.currentTimeMillis();
        txt_results.setText(retDocs.size()+" results at "+(end-start)+" milliseconds");

    }
    private String[] infixToPostfix2(String[] exp) {

        while (!stack.empty())
            stack.pop();

        int size = exp.length;
        String[] result = new String[size];
        int resultIndex = 0;

        for (int i = 0; i<size; ++i) {

            String str = exp[i];


            if (is_word(str) || str.equals("*"))
                result[resultIndex++] = str;

            else if (str.equals("("))
                stack.push(str);

            else if (str.equals(")")) {

                while (!stack.isEmpty() && !stack.peek().equals("("))
                    result[resultIndex++] = (String) stack.pop();

                if (!stack.isEmpty() && !stack.peek().equals("("))
                    return null;
                else
                    stack.pop();
            }

            else {
                while (!stack.isEmpty() && precedence(str)<=precedence((String) stack.peek()))
                    result[resultIndex++] = (String) stack.pop();
                stack.push(str);
            }

        }

        while (!stack.isEmpty())
            result[resultIndex] = (String) stack.pop();

        return result;
    }
    private boolean is_word(String str) {
        return str.length()>0 && !is_logic(str) && !str.equals("(") && !str.equals(")");
    }
    private boolean is_logic(String str) {
        switch (str) {
            case "NOT":
            case "AND":
            case "OR":
                return true;
            default:
                return false;
        }
    }
    private int precedence(String str) {
        switch (str) {
            case "NOT":
                return 2;
            case "AND":
            case "OR":
                return 1;
            default:
                return 0;
        }
    }
    private BitSet calculate(String[] result) {

        while (!stack.empty())
            stack.pop();

        for (String str : result) {
            if (str!=null) {

                if (is_word(str) || str.equals("*")) {

                    if (str.equals("*")) {
                        if (!stack.empty())
                            stack.push(stack.peek());
                    } else {
                        Log.d("YaMahdi", "is word: " + str);
                        int first = words.indexOf(str);
                        if (first>=0) {
                            Log.d("YaMahdi", "word [" + str + "] index: " + first);
                            BitSet bitSet = bitSetList.get(first);
                            stack.push(bitSet);
                        }
                    }

                } else if (is_logic(str)) {

                    BitSet secondBitSet = (BitSet) stack.pop();
                    if (!stack.empty()) {
                        BitSet firstBitSet = (BitSet) stack.pop();

                        switch (str) {
                            case "NOT":
                                break;
                            case "AND":
                                firstBitSet.and(secondBitSet);
                                stack.push(firstBitSet);
                                break;
                            case "OR":
                                firstBitSet.or(secondBitSet);
                                stack.push(firstBitSet);
                                break;
                        }
                    } else {
                        stack.push(secondBitSet);
                    }
                }

            } else {
                return (BitSet) stack.pop();
            }
        }

        if (!stack.empty())
            return (BitSet) stack.pop();
        else
            return new BitSet(0);
    }


    private void createDocItemList(List<Integer> retDocs) {

        int size = docItemList.size();
        docItemList.clear();
        docRecyclerViewAdapter.notifyDataSetChanged();

        String docsPath = path + File.separator + "Docs";
        File docFilePath = new File(docsPath);
        if (!docFilePath.exists())
            docFilePath.mkdirs();
        File[] docs = docFilePath.listFiles();
        FileInputStream fis;
        BufferedReader reader;

        for (Integer docId : retDocs) {
            File doc = docs[docId];

            try {
                FileInputStream fileInputStream = new FileInputStream(doc);
                byte[] data = new byte[fileInputStream.available()];
                fileInputStream.read(data);
                String json = new String(data, "UTF-8");
                JSONObject jsonObject = new JSONObject(json);

                String date = jsonObject.getString("date");
                String author = jsonObject.getString("author");
                String text = jsonObject.getString("text");
                String favorite = jsonObject.getString("favorite");
                String car = jsonObject.getString("car");
                car = car.replaceAll("_", " ");

                DocItem docItem = new DocItem(docId, date, author, car, text, favorite);
                docItemList.add(docItem);
                docRecyclerViewAdapter.notifyItemInserted(docItemList.size()-1);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        docRecyclerViewAdapter.notifyDataSetChanged();
    }


    List<String> stopWordSet = new ArrayList<>();
    List<String> stemmedStopWordSet;
    Stemmer stemmer = new Stemmer();
    private void prepareQuerying(final boolean parse) {

        createProgressDialog();

        path = getExternalFilesDir(null).getAbsolutePath();

        new Thread(new Runnable() {
            @Override
            public void run() {

                setThreadPriority(-19);

                if (parse) {

                    //**********************************************************************************
                    // parse tag files and create doc files
                    try {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.show();
                                progressDialog.setMessage("parsing...");
                            }
                        });

                        String docsPath = path + File.separator + "Docs";
                        File docFilePath = new File(docsPath);
                        if (!docFilePath.exists())
                            docFilePath.mkdirs();

                        String[] list = getAssets().list("2009");
                        int docId = 0;
                        for (String file : list) {

                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(getAssets().open("2009/"+file)));

                            while (reader.ready()) {
                                try {

                                    reader.readLine();
                                    String doc = reader.readLine();
                                    if (doc!=null && doc.equals("<DOC>")) {

                                        String date = reader.readLine();
                                        date = date.substring(date.indexOf(">")+1, date.lastIndexOf("<"));

                                        String author = reader.readLine();
                                        author = author.substring(author.indexOf(">")+1, author.lastIndexOf("<"));
                                        author = author.replaceAll("[\\\\/:*?\"<>|]", "-");


                                        String text = reader.readLine();
                                        while (!text.endsWith("</TEXT>"))
                                            text += reader.readLine();
                                        text = text.substring(text.indexOf(">")+1, text.lastIndexOf("<"));

                                        String favorite = reader.readLine();
                                        while (!favorite.endsWith("</FAVORITE>"))
                                            favorite += reader.readLine();
                                        favorite = favorite.substring(favorite.indexOf(">")+1, favorite.lastIndexOf("<"));

                                        String docFileName = "doc-"+(docId++)+".txt";

                                        JSONObject jsonObject = new JSONObject();
                                        jsonObject.put("date", date);
                                        jsonObject.put("author", author);
                                        jsonObject.put("text", text);
                                        jsonObject.put("favorite", favorite);
                                        jsonObject.put("car", file.substring(5));
                                        String write = jsonObject.toString();

                                        File docFile = new File(docsPath, docFileName);
                                        FileOutputStream fileOutputStream = new FileOutputStream(docFile, false);
                                        fileOutputStream.write(write.getBytes("UTF-8"));
                                        fileOutputStream.flush();
                                        fileOutputStream.close();

                                        //Log.d("Meeee", "Doc with id["+(docId-1)+"] created");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                        }








                        //******************************************************************************
                        // create dic and occurance files

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.setMessage("processing dictionary and occurance...");
                            }
                        });


                        File dic = new File(path, "dic.txt");
                        FileOutputStream fosDic = new FileOutputStream(dic, false);
                        BufferedWriter dicFileOutputStream = new BufferedWriter(new OutputStreamWriter(fosDic));
                        List<String> wordsToStoreInDicFile = new ArrayList<>();

                        File occu = new File(path, "occu.txt");
                        FileOutputStream fosOccu = new FileOutputStream(occu, false);
                        BufferedWriter occuFileOutputStream = new BufferedWriter(new OutputStreamWriter(fosOccu));


                        BufferedReader stpw = new BufferedReader(
                                new InputStreamReader(getAssets().open("stopwords.txt")));
                        while (stpw.ready()) {
                            stopWordSet.add(stpw.readLine());
                        }
                        stemmedStopWordSet = stemStringSet(stopWordSet);


                        File[] listOfDocFile = new File(docsPath).listFiles();
                        docId = 0;
                        for (File file : listOfDocFile) {

                            FileInputStream fileInputStream = new FileInputStream(file);
                            byte[] data = new byte[fileInputStream.available()];
                            fileInputStream.read(data);
                            String json = new String(data, "UTF-8");
                            JSONObject jsonObject = new JSONObject(json);

                            String text = jsonObject.getString("text");

                            //str = str.replaceAll("[0123456789]", "");
                            text = text.replaceAll("[/_\",.+*\\-&\\[\\](){}�!?$@<~>:=;%#\\\\]", " ");

                            String[] words = text.split("\\s+");
                            List<String> wordsToStoreInOccuFile = new ArrayList<>();

                            boolean stem = true;

                            for (String word : words) {

                                word = normalWord(word);

                                if (!word.equals("")) {

                                    if (!wordsToStoreInOccuFile.contains(word)) {

                                        wordsToStoreInOccuFile.add(word);
                                        occuFileOutputStream.write(docId + " : " + word);
                                        occuFileOutputStream.newLine();
                                        occuFileOutputStream.flush();

                                        if (!wordsToStoreInDicFile.contains(word)) {
                                            wordsToStoreInDicFile.add(word);
                                        }
                                    }
                                }
                            }

                            Log.i("Meeee", "occurrences from doc["+docId+"] writed");
                            docId++;

                        }

                        occuFileOutputStream.write(String.valueOf(docId));
                        occuFileOutputStream.newLine();
                        occuFileOutputStream.flush();
                        occuFileOutputStream.close();
                        Log.d("Meeee", "occurrence file created");
                        Log.d("Meeee", "dic count: "+docId);


                        Collections.sort(wordsToStoreInDicFile);
                        for (String word : wordsToStoreInDicFile) {
                            dicFileOutputStream.write(word);
                            dicFileOutputStream.newLine();
                            dicFileOutputStream.flush();
                        }
                        dicFileOutputStream.close();
                        Log.d("Meeee", "dictionary file created");


                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                    //**********************************************************************************
                    // create matrix file from dic and occurance files

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setMessage("processing matrix...");
                        }
                    });


                    try {

                        File dic = new File(path, "dic.txt");
                        FileInputStream fisDic = new FileInputStream(dic);
                        BufferedReader readerDic = new BufferedReader(new InputStreamReader(fisDic));
                        List<String> dicList = new ArrayList<>();
                        while (readerDic.ready()) {
                            dicList.add(readerDic.readLine());
                        }


                        File occuFile = new File(path, "occu.txt");
                        FileInputStream fisOccu = new FileInputStream(occuFile);
                        BufferedReader readerOccu = new BufferedReader(new InputStreamReader(fisOccu));
                        List<String> occuList = new ArrayList<>();
                        while (readerOccu.ready()) {
                            occuList.add(readerOccu.readLine());
                        }

                        int last = occuList.size();
                        int docsCount = Integer.parseInt(occuList.get(last-1));
                        occuList.remove(last-1);

                        boolean[][] booleans = new boolean[dicList.size()][docsCount];

                        int setTrues = 0;
                        for (String occu : occuList) {
                            int colon = occu.indexOf(":");
                            int docId = Integer.parseInt(occu.substring(0, colon-1));
                            String word = occu.substring(colon+2);
                            int index = dicList.indexOf(word);
                            if (index>=0) {
                                booleans[index][docId] = true;
                                setTrues++;
                                //Log.d("Meeeee", "booleans["+index+"]["+docId+"] = true");
                            }
                        }
                        Log.d("Meeeee", "set trues: "+setTrues);

                        File matrix = new File(path, "matrix.txt");
                        FileOutputStream fosMatrix = new FileOutputStream(matrix, false);
                        BufferedWriter writerMatrix = new BufferedWriter(new OutputStreamWriter(fosMatrix));

                        writerMatrix.write(dicList.size()+" : "+docsCount);
                        writerMatrix.write("\r\n");
                        writerMatrix.flush();

                        for (int i=0; i<dicList.size(); i++) {
                            writerMatrix.write(dicList.get(i)+" : ");
                            for (int j=0; j<docsCount; j++) {
                                if (booleans[i][j])
                                    writerMatrix.write("1");
                                else
                                    writerMatrix.write("0");
                            }
                            writerMatrix.write("\r\n");
                            writerMatrix.flush();
                        }

                        writerMatrix.flush();
                        writerMatrix.close();
                        Log.d("Meeeee", "booleans created");

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }


                //**********************************************************************************
                // read matrix and prepare to run query

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.show();
                        progressDialog.setMessage("load matrix...");
                    }
                });

                try {
                    Log.d("Meeeee", "indexing started");

                    if (!parse) {
                        BufferedReader stpw = new BufferedReader(
                                new InputStreamReader(getAssets().open("stopwords.txt")));
                        while (stpw.ready()) {
                            stopWordSet.add(stpw.readLine());
                        }
                        stemmedStopWordSet = stemStringSet(stopWordSet);
                    }


                    File matrix = new File(path, "matrix.txt");
                    FileInputStream fisMatrix = new FileInputStream(matrix);
                    BufferedReader readerMatrix = new BufferedReader(new InputStreamReader(fisMatrix));

                    String matrixSize = readerMatrix.readLine();
                    int colon = matrixSize.indexOf(":");
                    int x = Integer.parseInt(matrixSize.substring(0, colon-1));
                    int y = Integer.parseInt(matrixSize.substring(colon+2));
                    bitSetList = new ArrayList<>(x);
                    words = new ArrayList<>(x);
                    String line = "";
                    while (readerMatrix.ready()) {
                        line = readerMatrix.readLine();
                        colon = line.indexOf(":");
                        words.add(line.substring(0, colon-1));
                        colon+=2; // start of binary
                        BitSet bitSet = new BitSet(y);
                        for (int i=colon; i<line.length(); i++) {
                            if (line.charAt(i)=='1')
                                bitSet.set(i-colon, true);
                        }
                        bitSetList.add(bitSet);
                        Log.d("Meeeee", "bitSetList size: "+bitSetList.size());
                    }

                    Log.d("Meeeee", "indexing finshed");


                } catch (Exception e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        docItemList.clear();
                        docRecyclerViewAdapter.notifyDataSetChanged();
                    }
                });


            }
        }).start();

    }

    private String normalWord(String word) {

        if (!word.equals("")) {

            // ignore numeric
            if (word.matches("\\d+"))
                return "";

            if (word.contains("`"))
                word = word.replaceAll("`", "'");

            if (word.contains("'")) {
                // 'word' -> word.  didn't
                if (word.startsWith("'")) word = word.substring(1);
                if (word.endsWith("'"))
                    word = word.substring(0, word.length() - 1);
                if (word.contains("'")) {
                    int op = word.indexOf("'");
                    if (op == 0 && word.length() > 1)
                        word = word.substring(1);
                    if (op > 0 && word.charAt(op - 1) == 'n' && word.charAt(op + 1) == 't')
                        return "";
                    word = word.substring(0, op);
                }
            }


            boolean stem = true;
            if (word.toUpperCase().equals(word)) {
                // all char is uppercase
                stem = false;
            }
            if (word.matches(".*\\d+.*")) {
                // word is like: TXB4
                stem = false;
            }
            word = word.toLowerCase();


            if (!isStopword(word) && !isStemmedStopword(word)) {

                if (stem)
                    word = stemmer.stem(word);

                return word;
            }
        }

        return "";
    }

    private void createProgressDialog() {
        if (progressDialog==null) {
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setTitle("Please Wait");
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
    }
    public boolean isStopword(String word) {
        if(word.length() < 2) return true;
        if(word.charAt(0) >= '0' && word.charAt(0) <= '9') return true; //remove numbers, "25th", etc
        if(stopWordSet.contains(word)) return true;
        else return false;
    }
    public boolean isStemmedStopword(String word) {
        if(word.length() < 2) return true;
        if(word.charAt(0) >= '0' && word.charAt(0) <= '9') return true; //remove numbers, "25th", etc
        String stemmed = stemString(word);
        if(stopWordSet.contains(stemmed)) return true;
        if(stemmedStopWordSet.contains(stemmed)) return true;
        if(stopWordSet.contains(word)) return true;
        if(stemmedStopWordSet.contains(word)) return true;
        else return false;
    }
    public String stemString(String string) {
        return new Stemmer().stem(string);
    }
    public List<String> stemStringSet(List<String> stringSet) {
        Stemmer stemmer = new Stemmer();
        List<String> results = new ArrayList<>();
        for(String string : stringSet) {
            results.add(stemmer.stem(string));
        }
        return results;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.parse:
                prepareQuerying(true);
                break;
        }
        return true;
    }

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }





    //*************************************************************************
    public class Stemmer {

        private char[] b;
        private int i,     /* offset into b */
                i_end, /* offset to end of stemmed word */
                j, k;
        private static final int INC = 50;

        /* unit of size whereby b is increased */
        public Stemmer() {
            b = new char[INC];
            i = 0;
            i_end = 0;
        }

        /**
         * Add a character to the word being stemmed.  When you are finished
         * adding characters, you can call stem(void) to stem the word.
         */

        public void add(char ch) {
            if (i == b.length) {
                char[] new_b = new char[i + INC];
                for (int c = 0; c < i; c++) new_b[c] = b[c];
                b = new_b;
            }
            b[i++] = ch;
        }


        /**
         * Adds wLen characters to the word being stemmed contained in a portion
         * of a char[] array. This is like repeated calls of add(char ch), but
         * faster.
         */

        public void add(char[] w, int wLen) {
            if (i + wLen >= b.length) {
                char[] new_b = new char[i + wLen + INC];
                for (int c = 0; c < i; c++) new_b[c] = b[c];
                b = new_b;
            }
            for (int c = 0; c < wLen; c++) b[i++] = w[c];
        }

        /**
         * After a word has been stemmed, it can be retrieved by toString(),
         * or a reference to the internal buffer can be retrieved by getResultBuffer
         * and getResultLength (which is generally more efficient.)
         */
        public String toString() {
            return new String(b, 0, i_end);
        }

        /**
         * Returns the length of the word resulting from the stemming process.
         */
        public int getResultLength() {
            return i_end;
        }

        /**
         * Returns a reference to a character buffer containing the results of
         * the stemming process.  You also need to consult getResultLength()
         * to determine the length of the result.
         */
        public char[] getResultBuffer() {
            return b;
        }

        /* cons(i) is true <=> b[i] is a consonant. */

        private final boolean cons(int i) {
            switch (b[i]) {
                case 'a':
                case 'e':
                case 'i':
                case 'o':
                case 'u':
                    return false;
                case 'y':
                    return (i == 0) ? true : !cons(i - 1);
                default:
                    return true;
            }
        }

   /* m() measures the number of consonant sequences between 0 and j. if c is
      a consonant sequence and v a vowel sequence, and <..> indicates arbitrary
      presence,
         <c><v>       gives 0
         <c>vc<v>     gives 1
         <c>vcvc<v>   gives 2
         <c>vcvcvc<v> gives 3
         ....
   */

        private final int m() {
            int n = 0;
            int i = 0;
            while (true) {
                if (i > j) return n;
                if (!cons(i)) break;
                i++;
            }
            i++;
            while (true) {
                while (true) {
                    if (i > j) return n;
                    if (cons(i)) break;
                    i++;
                }
                i++;
                n++;
                while (true) {
                    if (i > j) return n;
                    if (!cons(i)) break;
                    i++;
                }
                i++;
            }
        }

        /* vowelinstem() is true <=> 0,...j contains a vowel */

        private final boolean vowelinstem() {
            int i;
            for (i = 0; i <= j; i++) if (!cons(i)) return true;
            return false;
        }

        /* doublec(j) is true <=> j,(j-1) contain a double consonant. */

        private final boolean doublec(int j) {
            if (j < 1) return false;
            if (b[j] != b[j - 1]) return false;
            return cons(j);
        }

   /* cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
      and also if the second c is not w,x or y. this is used when trying to
      restore an e at the end of a short word. e.g.
         cav(e), lov(e), hop(e), crim(e), but
         snow, box, tray.
   */

        private final boolean cvc(int i) {
            if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2)) return false;
            {
                int ch = b[i];
                if (ch == 'w' || ch == 'x' || ch == 'y') return false;
            }
            return true;
        }

        private final boolean ends(String s) {
            int l = s.length();
            int o = k - l + 1;
            if (o < 0) return false;
            for (int i = 0; i < l; i++) if (b[o + i] != s.charAt(i)) return false;
            j = k - l;
            return true;
        }

   /* setto(s) sets (j+1),...k to the characters in the string s, readjusting
      k. */

        private final void setto(String s) {
            int l = s.length();
            int o = j + 1;
            for (int i = 0; i < l; i++) b[o + i] = s.charAt(i);
            k = j + l;
        }

        /* r(s) is used further down. */

        private final void r(String s) {
            if (m() > 0) setto(s);
        }

   /* step1() gets rid of plurals and -ed or -ing. e.g.
          caresses  ->  caress
          ponies    ->  poni
          ties      ->  ti
          caress    ->  caress
          cats      ->  cat
          feed      ->  feed
          agreed    ->  agree
          disabled  ->  disable
          matting   ->  mat
          mating    ->  mate
          meeting   ->  meet
          milling   ->  mill
          messing   ->  mess
          meetings  ->  meet
   */

        private final void step1() {
            if (b[k] == 's') {
                if (ends("sses")) k -= 2;
                else if (ends("ies")) setto("i");
                else if (b[k - 1] != 's') k--;
            }
            if (ends("eed")) {
                if (m() > 0) k--;
            } else if ((ends("ed") || ends("ing")) && vowelinstem()) {
                k = j;
                if (ends("at")) setto("ate");
                else if (ends("bl")) setto("ble");
                else if (ends("iz")) setto("ize");
                else if (doublec(k)) {
                    k--;
                    {
                        int ch = b[k];
                        if (ch == 'l' || ch == 's' || ch == 'z') k++;
                    }
                } else if (m() == 1 && cvc(k)) setto("e");
            }
        }

        /* step2() turns terminal y to i when there is another vowel in the stem. */

        private final void step2() {
            if (ends("y") && vowelinstem()) b[k] = 'i';
        }

   /* step3() maps double suffices to single ones. so -ization ( = -ize plus
      -ation) maps to -ize etc. note that the string before the suffix must give
      m() > 0. */

        private final void step3() {
            if (k == 0) return; /* For Bug 1 */
            switch (b[k - 1]) {
                case 'a':
                    if (ends("ational")) {
                        r("ate");
                        break;
                    }
                    if (ends("tional")) {
                        r("tion");
                        break;
                    }
                    break;
                case 'c':
                    if (ends("enci")) {
                        r("ence");
                        break;
                    }
                    if (ends("anci")) {
                        r("ance");
                        break;
                    }
                    break;
                case 'e':
                    if (ends("izer")) {
                        r("ize");
                        break;
                    }
                    break;
                case 'l':
                    if (ends("bli")) {
                        r("ble");
                        break;
                    }
                    if (ends("alli")) {
                        r("al");
                        break;
                    }
                    if (ends("entli")) {
                        r("ent");
                        break;
                    }
                    if (ends("eli")) {
                        r("e");
                        break;
                    }
                    if (ends("ousli")) {
                        r("ous");
                        break;
                    }
                    break;
                case 'o':
                    if (ends("ization")) {
                        r("ize");
                        break;
                    }
                    if (ends("ation")) {
                        r("ate");
                        break;
                    }
                    if (ends("ator")) {
                        r("ate");
                        break;
                    }
                    break;
                case 's':
                    if (ends("alism")) {
                        r("al");
                        break;
                    }
                    if (ends("iveness")) {
                        r("ive");
                        break;
                    }
                    if (ends("fulness")) {
                        r("ful");
                        break;
                    }
                    if (ends("ousness")) {
                        r("ous");
                        break;
                    }
                    break;
                case 't':
                    if (ends("aliti")) {
                        r("al");
                        break;
                    }
                    if (ends("iviti")) {
                        r("ive");
                        break;
                    }
                    if (ends("biliti")) {
                        r("ble");
                        break;
                    }
                    break;
                case 'g':
                    if (ends("logi")) {
                        r("log");
                        break;
                    }
            }
        }

        /* step4() deals with -ic-, -full, -ness etc. similar strategy to step3. */

        private final void step4() {
            switch (b[k]) {
                case 'e':
                    if (ends("icate")) {
                        r("ic");
                        break;
                    }
                    if (ends("ative")) {
                        r("");
                        break;
                    }
                    if (ends("alize")) {
                        r("al");
                        break;
                    }
                    break;
                case 'i':
                    if (ends("iciti")) {
                        r("ic");
                        break;
                    }
                    break;
                case 'l':
                    if (ends("ical")) {
                        r("ic");
                        break;
                    }
                    if (ends("ful")) {
                        r("");
                        break;
                    }
                    break;
                case 's':
                    if (ends("ness")) {
                        r("");
                        break;
                    }
                    break;
            }
        }

        /* step5() takes off -ant, -ence etc., in context <c>vcvc<v>. */

        private final void step5() {
            if (k == 0) return; /* for Bug 1 */
            switch (b[k - 1]) {
                case 'a':
                    if (ends("al")) break;
                    return;
                case 'c':
                    if (ends("ance")) break;
                    if (ends("ence")) break;
                    return;
                case 'e':
                    if (ends("er")) break;
                    return;
                case 'i':
                    if (ends("ic")) break;
                    return;
                case 'l':
                    if (ends("able")) break;
                    if (ends("ible")) break;
                    return;
                case 'n':
                    if (ends("ant")) break;
                    if (ends("ement")) break;
                    if (ends("ment")) break;
                    /* element etc. not stripped before the m */
                    if (ends("ent")) break;
                    return;
                case 'o':
                    if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) break;
                    /* j >= 0 fixes Bug 2 */
                    if (ends("ou")) break;
                    return;
                /* takes care of -ous */
                case 's':
                    if (ends("ism")) break;
                    return;
                case 't':
                    if (ends("ate")) break;
                    if (ends("iti")) break;
                    return;
                case 'u':
                    if (ends("ous")) break;
                    return;
                case 'v':
                    if (ends("ive")) break;
                    return;
                case 'z':
                    if (ends("ize")) break;
                    return;
                default:
                    return;
            }
            if (m() > 1) k = j;
        }

        /* step6() removes a final -e if m() > 1. */

        private final void step6() {
            j = k;
            if (b[k] == 'e') {
                int a = m();
                if (a > 1 || a == 1 && !cvc(k - 1)) k--;
            }
            if (b[k] == 'l' && doublec(k) && m() > 1) k--;
        }

        /**
         * Stem the word placed into the Stemmer buffer through calls to add().
         * Returns true if the stemming process resulted in a word different
         * from the input.  You can retrieve the result with
         * getResultLength()/getResultBuffer() or toString().
         */
        public void stem() {
            k = i - 1;
            if (k > 1) {
                step1();
                step2();
                step3();
                step4();
                step5();
                step6();
            }
            i_end = k + 1;
            i = 0;
        }

        public String stem(String s) {
            for (char c : s.toCharArray()) {
                add(c);
            }
            stem();
            return toString();
        }
    }
    //*************************************************************************
    public class DocItem {

        int docId;
        String date;
        String author;
        String car;
        String text;
        String favorite;

        public DocItem(int docId, String date, String author, String car, String text, String favorite) {
            this.docId = docId;
            this.date = date;
            this.author = author;
            this.car = car;
            this.text = text;
            this.favorite = favorite;
        }
    }
    //*************************************************************************
    public class DocItemAdapter extends BaseAdapter {

        private List<DocItem> docItemList;
        private LayoutInflater inflater;
        private Listener listener;

        public DocItemAdapter(MainActivity mainActivity, List<DocItem> docItemList, Listener listener) {
            this.docItemList = docItemList;
            this.listener = listener;
            inflater = (LayoutInflater) mainActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int p, View view, ViewGroup viewGroup) {

            final int position = p;

            final View rowView = inflater.inflate(R.layout.itemlist_doc, viewGroup, false);
            final DocItem docItem = docItemList.get(position);

            return rowView;
        }

        @Override
        public int getCount() {
            return docItemList.size();
        }

        @Override
        public Object getItem(int position) {
            return docItemList.get(position);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

    }
    public interface Listener {
        void onClick(int position);
    }
    //*************************************************************************
    public class DocRecyclerViewAdapter extends RecyclerView.Adapter<DocRecyclerViewAdapter.ViewHolder> {

        MainActivity mainActivity;
        List<DocItem> docItemList;

        public DocRecyclerViewAdapter(MainActivity mainActivity, List<DocItem> docItemList) {
            this.mainActivity = mainActivity;
            this.docItemList = docItemList;
        }

        @Override
        public DocRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.itemlist_doc, parent, false);
            return new DocRecyclerViewAdapter.ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull DocRecyclerViewAdapter.ViewHolder holder, final int position) {

            DocItem docItem = docItemList.get(position);
            holder.txt_docId.setText(String.valueOf(docItem.docId));
            holder.txt_author.setText(docItem.author);
            holder.txt_car.setText(docItem.car);
            holder.txt_text.setText(docItem.text);
            holder.txt_date.setText(docItem.date);

            holder.rlv_parent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onClick(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return docItemList.size();
        }


        //**********************************************************************************************

        public class ViewHolder extends RecyclerView.ViewHolder {
            RelativeLayout rlv_parent;
            AppCompatTextView txt_docId, txt_author, txt_car, txt_text, txt_date;
            public ViewHolder(View view) {
                super(view);
                rlv_parent = view.findViewById(R.id.rlv_itemlistdocitem_parent);
                txt_docId = view.findViewById(R.id.txt_itemlistdoc_docId);
                txt_author = view.findViewById(R.id.txt_itemlistdoc_author);
                txt_car = view.findViewById(R.id.txt_itemlistdoc_car);
                txt_text = view.findViewById(R.id.txt_itemlistdoc_text);
                txt_date = view.findViewById(R.id.txt_itemlistdoc_date);
            }
        }

        //**********************************************************************************************

        DocRecyclerViewAdapterListener listener;
        public void setListener(DocRecyclerViewAdapterListener listener) {
            this.listener = listener;
        }
    }
    interface DocRecyclerViewAdapterListener {
        void onClick(int position);
    }
    //*************************************************************************
    public static class DocViewFragment extends Fragment {

        AppCompatTextView txt_docId, txt_author, txt_car, txt_date, txt_text, txt_favorite;
        DocItem docItem;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_docview, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            findViewsById(view);

            txt_docId.setText(String.valueOf(docItem.docId));
            txt_author.setText(docItem.author);
            txt_car.setText(docItem.car);
            txt_date.setText(docItem.date);
            txt_text.setText(docItem.text);
            txt_favorite.setText(docItem.favorite);

        }

        //**********************************************************************************************

        private void findViewsById(View view) {
            txt_docId = view.findViewById(R.id.txt_fragmentdocview_docId);
            txt_author = view.findViewById(R.id.txt_fragmentdocview_author);
            txt_car = view.findViewById(R.id.txt_fragmentdocview_car);
            txt_date = view.findViewById(R.id.txt_fragmentdocview_date);
            txt_text = view.findViewById(R.id.txt_fragmentdocview_text);
            txt_favorite = view.findViewById(R.id.txt_fragmentdocview_favorite);
        }
    }

}
