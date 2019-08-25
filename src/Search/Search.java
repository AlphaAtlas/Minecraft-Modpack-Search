package Search;
import com.therandomlabs.curseapi.project.Category;
import com.therandomlabs.curseapi.project.CurseProject;
import com.therandomlabs.curseapi.RelationType;
import com.therandomlabs.curseapi.file.CurseFileList;
import com.therandomlabs.curseapi.CurseException;
import com.therandomlabs.curseapi.InvalidCurseForgeProjectException;
//import com.therandomlabs.curseapi.file.CurseFile;
import com.therandomlabs.curseapi.project.Relation;
import com.therandomlabs.utils.collection.TRLList;
import com.therandomlabs.utils.logging.Logging;
import com.therandomlabs.utils.misc.ThreadUtils;
import com.therandomlabs.curseapi.CurseAPI;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Search {

    
    public static void main(String[] args) throws Exception {

        ZonedDateTime today = ZonedDateTime.now();
        int downloadWeight = 1;
        int updateWeight = 1;
        int threadMulti = 2;

        Logging.getLogger().disableDebug();
        Logging.getLogger().info("Hello CurseForge!");
        ConcurrentSkipListSet<String> URLList = new ConcurrentSkipListSet<String>();
        URLList.add("https://www.curseforge.com/minecraft/mc-mods/openmodularturrets");
        URLList.add("https://www.curseforge.com/minecraft/mc-mods/omlib");
        URLList.add("https://www.curseforge.com/minecraft/mc-mods/galacticraft-add-on-more-planets");

        CopyOnWriteArrayList<TRLList<Relation>> packListList = new CopyOnWriteArrayList<TRLList<Relation>>();
        ThreadUtils.splitWorkload(Math.min(CurseAPI.getMaximumThreads()*threadMulti, URLList.size()), URLList, URL ->{
           packListList.add(CurseProject.fromURL(URL).dependents(RelationType.INCLUDE));
           Logging.getLogger().info("Downloaded dependents for mod at " + URL);
        });

        //Find intersection, adapted from StackOverflow 
        //
        if(packListList.isEmpty()){
            Logging.getLogger().fatalError("No Valid URLs!");
        }
        CopyOnWriteArrayList<Relation> commonDependents = packListList.get(0).stream().collect(Collectors.toCollection(CopyOnWriteArrayList::new));
        if(packListList.size() > 1){
            packListList.remove(packListList.get(0));
            for(TRLList<Relation> l: packListList){
                commonDependents.retainAll(l);
            }       
        }
        //commonDependents = commonDependents.parallelStream().filter(l::contains).collect(Collectors.toCollection(ConcurrentSkipListSet::new));
        Logging.getLogger().info("Filtered mod lists.");
        
        ConcurrentSkipListSet<CurseProject> filtered = new ConcurrentSkipListSet<CurseProject>(new Comparator<CurseProject>(){
            @Override
            public int compare(CurseProject p1, CurseProject p2) {
                int d1, d2;
                try {
                    d1 = p1.downloads();
                }
                catch(Exception e)
                {
                    Logging.getLogger().error("Error getting downloads for " + p1.title());
                    d1 = 1;
                }
                try {
                    d2 = p2.downloads();
                }
                catch(Exception e)
                {
                    Logging.getLogger().error("Error getting downloads for " + p2.title());
                    d2 = 1;
                }

                ZonedDateTime u1, u2;
                try {
                    u1 = p1.lastUpdateTime();
                }
                catch(Exception e)
                {
                    Logging.getLogger().error("Error getting update time for " + p1.title());
                    u1 = today.minusYears(3);
                }
                try {
                    u2 = p2.lastUpdateTime();
                }
                catch(Exception e)
                {
                    Logging.getLogger().error("Error getting update time for " + p2.title());
                    u2 = today.minusYears(3);
                }
                //weigh the sortinga
                return Double.compare((downloadWeight * Math.log(d1) - updateWeight * Math.sqrt(u1.until(today, ChronoUnit.DAYS))), downloadWeight * Math.log(d2) - updateWeight * Math.sqrt(u2.until(today, ChronoUnit.DAYS)));
            }
        });
        
        ThreadUtils.splitWorkload(CurseAPI.getMaximumThreads()*threadMulti, commonDependents, temp ->{
            
            try {
                CurseProject p = CurseProject.fromURL(temp.url());
                CurseFileList f = p.files(); 
                if( !p.isNull() && !f.isEmpty()) {
                    try { 
                        if(((f.size() > 1) || f.get(0).uploadTime().isAfter(today.minusMonths(1))) && f.get(0).uploadTime().isAfter(today.minusMonths(4))) {
                            TRLList<Category> cats = p.categories();
                            for (Category c : cats) {
                                //Logging.getLogger().info(c.name());
                                if(c.name().equals("Multiplayer")){       
                                    filtered.add(p);
                                    Logging.getLogger().info("Modpack added!");
                                    break;         
                                }              
                            }
                        }
                    }   
                    catch(Exception e){
                        Logging.getLogger().error("Could not download file info for" + p.title());
                        Logging.getLogger().debug(e.getStackTrace().toString());
                    }
                }
            }
            catch(InvalidCurseForgeProjectException e) {
                Logging.getLogger().error(e.toString());
                Logging.getLogger().debug(e.getStackTrace().toString());
            }
            catch(CurseException e)
            {
                Logging.getLogger().info("CurseAPI is unhappy!");
                Logging.getLogger().error("Failed to fetch " + temp.url());
                Logging.getLogger().debug(e.getStackTrace().toString());
                
            }
            catch(Exception e)
            {
                Logging.getLogger().error("Failed to fetch " + temp.url());
                Logging.getLogger().debug(e.getStackTrace().toString());
            }
        });

        
        for(CurseProject p : filtered){
            Logging.getLogger().info(p.urlString() + "  D: " + p.downloads() + "  U: " + p.lastUpdateTime().truncatedTo(ChronoUnit.DAYS));
        }
		
        //Logging.getLogger().info(result);

    }
}