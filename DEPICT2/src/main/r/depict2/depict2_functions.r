
# Function definitions
# ------------------------------------------------------ 
# NPG friendly ggplot theme
theme.nature <- function(p, base_size = 11, base_family = "ArialMT") {
  p <- p + theme_grey(base_size = base_size, base_family = base_family) %+replace%
    theme(panel.background = element_blank(),
          panel.border = element_blank(),
          panel.grid.major = element_blank(),
          panel.grid.minor = element_blank(),
          axis.line = element_line(color="black", size=0.5),
          axis.ticks = element_line(size=0.5),
          axis.text = element_text(size=base_size, family="ArialMT", face="plain"),
          strip.background = element_blank(),
          legend.key = element_blank(),
          legend.text = element_text(size=base_size, family = "ArialMT", face="plain"),
          complete = TRUE,
          plot.title = element_text(hjust=0.5))
  return(p)
}
# ------------------------------------------------------ 
# Plot depict2 scatterplot by enrichment score
make.tsne.plot <- function(data, trait, x="Annotation1", y="Annotation2", colour="Enrichment.Z.score") {
  data <- data[order(abs(data[,colour])),]
  # low  <- colorRampPalette(c('firebrick3', '#F6DCDC'))
  # mid  <- colorRampPalette(c("#F6DCDC", "#DDE6EE"))
  # high <- colorRampPalette(c('#DDE6EE', 'dodgerblue4'))
  # bins= 100
  # breaks = seq(-15, 15, length.out = bins)
  # colRamp <- c(low((bins-10)/2), mid(10), high((bins-10)/2))
  # cols <- colRamp[as.numeric(cut(data[,colour], breaks = breaks))]
  # 
  # pdf(width=7.5, height=7.5, file="~/Desktop/")
  # 
  # 
  # layout(matrix(1:2,ncol=2), width = c(2,1),height = c(1,1))
  # plot(data$Annotation1, data$Annotation2, pch = 20, col=adjustcolor(cols, alpha.f = 0.8), bty='n', xlab="t-SNE component 1", ylab="t-SNE compenent 2")
  # 
  # legend_image <- as.raster(matrix(colRamp, ncol=1))
  # plot(c(0,2),c(0,1),type = 'n', axes = F,xlab = '', ylab = '', main = 'Z-score')
  # text(x=1.5, y = seq(0,1,l=5), labels = seq(0,1,l=5))
  # rasterImage(legend_image, 0, 0, 1,1)
  # 
  #  legend("topright",legend=c("15", "0", "-15"),fill=c("dodgerblue4", "grey", "firebrick3"), title="Z-score", border=F, bty="n")
  # #legend.scale(c(-15, 15), col=cols)
  # 
  # p <- ggplot(aes(x=as.numeric(data[,x]),
  #                 y=as.numeric(data[,y]),
  #                 fill=data[,colour]),
  #             data=data) +
  #   geom_point(color=cols) +
  #   labs(title=trait) +
  #   xlab(x) +
  #   ylab(y) +
  #   scale_fill_continuous(name="Z-score", limits=c(-15, 15), low="firebrick3", mid="grey", high="dodgerblue4")
  
  p <- ggplot(aes(x=as.numeric(data[,x]), y=as.numeric(data[,y])), data=data) +
    labs(title=trait) + xlab(x) + ylab(y)
  
  p <- ggplot(aes(x=as.numeric(data[,x]), y=as.numeric(data[,y]), colour=data[,colour]), data=data) +
    geom_point() +
    labs(title=trait) + xlab(x) + ylab(y) +
    scale_colour_gradient(low=adjustcolor("red", alpha.f = 0.5),
                          high=adjustcolor("blue", alpha.f = 0.5),
                          limits=c(-15, 15),
                          name="Z-score")
  
  p <- theme.nature(p)
  return(p)
}


# ------------------------------------------------------ 
read.depict2 <- function(path) {
  potential_traits <- c("expression","expression_scP3","expression_brain","expression_gs_tcell","Coregulation","Coregulation_eQTLGen","Coregulation_brain","Reactome","GO_P","GO_C","GO_F","KEGG","HPO","GO_P_brain","GO_C_brain","GO_F_brain","KEGG_brain","HPO_brain","Reactome_brain","gtexV8","gtex")
  output <- list()
  for (sheet in potential_traits) {
    tmp <- tryCatch({data.frame(read_excel(path, sheet=sheet, col_types ="guess", trim_ws = T), stringsAsFactors=F)},
                    error=function(a){return(NA)},
                    warn=function(a){return(NA)})
    
    if(!is.na(tmp)) {
      
      for (i in 1:ncol(tmp)) {
        if (class(tmp[,i]) == "character"){
          tmp[,i] <- type.convert(tmp[,i], as.is=T)
          
        }
      }
      
      rownames(tmp) <- tmp[,1]
      output[[sheet]] <- tmp
    }
  }
  
  return(output)
}

# ------------------------------------------------------ 
make.zscore.matrix <- function(datasets, trait="Coregulation", collumn="Enrichment.Z.score") {
  out <- matrix()
  i=0
  for (dataset in names(datasets)) {
    tmp <- datasets[[dataset]][[trait]]
    if (i==0) {
      out <- matrix(tmp[,collumn])
      rownames(out) <- tmp[,1]
    } else {
      out <- cbind(out, tmp[rownames(out), collumn])
    }
    i <- i+1
  }
  colnames(out) <- names(datasets)
  
  return(out)
}

# ------------------------------------------------------ 
read.enrichments <- function(files, column=1) {
  out <- matrix()
  i=0
  for (dataset in files) {
    tmp <- read.table(dataset, stringsAsFactors = F, row.names = 1, header=T, sep="\t")
    if (i==0) {
      out <- as.matrix(tmp[,column, drop=F])
    } else {
      out <- cbind(out, tmp[rownames(out), column, drop=F])
    }
    i <- i+1
  }
  colnames(out) <- make.names(basename(files), unique=T)
  
  return(out)
}

# ------------------------------------------------------ 
read.enrichments.fread <- function(files, column=1) {
  out <- matrix()
  i=0
  for (dataset in files) {
    tmp <- fread(dataset, stringsAsFactors = F, header=T, sep="\t", data.table=F)
    rownames(tmp) <- make.names(tmp[,1], unique=T)
    tmp <- tmp[,-1]
    if (i==0) {
      out <- as.matrix(tmp[,column, drop=F])
    } else {
      out <- cbind(out, tmp[rownames(out), column, drop=F])
    }
    i <- i+1
  }
  colnames(out) <- basename(files)
  
  return(out)
}
# ------------------------------------------------------ 
cor.test.p <- function(x, method){
  FUN <- function(x, y) cor.test(x, y, method=method, use="complete.obs")[["p.value"]]
  z <- outer(
    colnames(x), 
    colnames(x), 
    Vectorize(function(i,j) FUN(x[,i], x[,j]))
  )
  dimnames(z) <- list(colnames(x), colnames(x))
  z
}

# ------------------------------------------------------ 
make.correlation.heatmap <- function(matrix, trait) {
  c <- cor(matrix, method="spearman", use="pairwise.complete.obs")
  #p <- cor.test.p(matrix, method="spearman")
  
  #c[p > 0.05] <- 0
  
  hm(c, main=trait)
}

# ------------------------------------------------------ 
hm <- function(data, cellwidth=12, cellheight=12, limit=NULL, ...) {
  break.list <- seq(-max(abs(data)), max(abs(data)), by=max(abs(data))/100)
  pheatmap(data,
           breaks=break.list,
           col=colorRampPalette(rev(brewer.pal(n=7, name ="RdBu")))(length(break.list)),
           cellwidth=cellwidth,
           cellheight=cellheight,
           ...)
}

# ------------------------------------------------------ 
simple.qq.plot <- function (observedPValues) {
  plot(-log10(1:length(observedPValues)/length(observedPValues)), 
       -log10(sort(observedPValues)))
  abline(0, 1, col = "red")
}
